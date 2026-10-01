"""Transport existing exported content; never parse ROM or invent game data."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import tempfile
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / "ci/content-source.json").read_text())
MAX_BYTES = 100 * 1024 * 1024


def sha(data):
    return hashlib.sha256(data).hexdigest()


def tool(name):
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk and os.name == "nt":
        sdk = str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk")
    if not sdk:
        raise ValueError("Android SDK is required")
    suffix = (".bat" if name == "apksigner" else ".exe") if os.name == "nt" else ""
    return str(Path(sdk) / "build-tools/35.0.0" / (name + suffix))


def command(args):
    result = subprocess.run(args, capture_output=True)
    if result.returncode:
        raise ValueError("APK tool verification failed")
    # Android build tools emit UTF-8, including Chinese application labels.
    # Windows locale decoding (GBK) can otherwise corrupt even a valid APK.
    return result.stdout.decode("utf-8")


def verify_apk(apk, release=False):
    cert = command([tool("apksigner"), "verify", "--print-certs", str(apk)])
    hashes = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)", cert)
    if hashes != [CONFIG["signerSha256"]]:
        raise ValueError("APK signer differs from existing installed application")
    badging = command([tool("aapt"), "dump", "badging", str(apk)])
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not match or match[1] != CONFIG["package"]:
        raise ValueError("Wrong APK package")
    if release and "application-debuggable" in badging:
        raise ValueError("Release APK must not be debuggable")
    return dict(package=match[1], versionCode=int(match[2]), versionName=match[3], signerSha256=hashes[0])


def validate_content_path(name):
    path = PurePosixPath(name)
    if not name or "\\" in name or ":" in name or path.is_absolute() or ".." in path.parts or name != str(path):
        raise ValueError("Unsafe content path")


def content(apk, pin=None):
    pin = pin or CONFIG
    prefix = "assets/development/"
    with zipfile.ZipFile(apk) as archive:
        entries = [x for x in archive.infolist() if x.filename.startswith(prefix) and not x.is_dir()]
        names = [x.filename[len(prefix):] for x in entries]
        if len(names) != len(set(names)) or len(names) > 256:
            raise ValueError("Duplicate or excessive content entries")
        if sum(x.file_size for x in entries) > MAX_BYTES:
            raise ValueError("Content size limit exceeded")
        for x, name in zip(entries, names):
            validate_content_path(name)
            if (x.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError("Content symlink rejected")
        manifest_bytes = archive.read(prefix + "manifest.json")
        if sha(manifest_bytes) != pin["manifestSha256"]:
            raise ValueError("Content manifest differs from reviewed export; update pin explicitly")
        manifest = json.loads(manifest_bytes)
        if manifest["version"] != pin["contentVersion"] or manifest["schemaVersion"] != 1:
            raise ValueError("Wrong development content version/schema")
        if set(names) != set(manifest["files"]) | {"manifest.json"}:
            raise ValueError("Content file set differs from manifest")
        payload = {name: archive.read(prefix + name) for name in names}
        for name, expected in manifest["files"].items():
            if sha(payload[name]) != expected:
                raise ValueError("Content checksum mismatch: " + name)
        return payload


def fetch(url, target):
    if not url.startswith("https://"):
        raise ValueError("Content source requires HTTPS")
    # URLs may contain protected query strings. Never print them or HTTP exception text.
    try:
        with urllib.request.urlopen(url, timeout=120) as response:
            if not response.url.startswith("https://"):
                raise ValueError("Insecure redirect")
            data = response.read(MAX_BYTES + 1)
        if len(data) > MAX_BYTES:
            raise ValueError("Source size limit exceeded")
        target.write_bytes(data)
    except Exception:
        raise ValueError("Content source download failed (URL redacted)") from None


def restore(source=None, destination=None, next_code=None):
    destination = destination or ROOT / "android/app/src/main/assets/development"
    with tempfile.TemporaryDirectory(prefix="fengshen-content-") as tmp:
        apk = Path(tmp) / "source.apk"
        if source:
            apk.write_bytes(Path(source).read_bytes())
        else:
            metadata = Path(tmp) / "version.json"
            fetch(CONFIG["metadataUrl"] + "?ci=" + str(time.time_ns()), metadata)
            release = json.loads(metadata.read_text())
            if release["package"] != CONFIG["package"] or release["signerSha256"] != CONFIG["signerSha256"]:
                raise ValueError("Untrusted source release metadata")
            fetch(CONFIG["apkUrl"] + "?v=" + str(int(release["versionCode"])), apk)
            if sha(apk.read_bytes()) != release["sha256"] or apk.stat().st_size != release["sizeBytes"]:
                raise ValueError("Source APK differs from verified release metadata")
        info = verify_apk(apk)
        if next_code is not None and next_code <= info["versionCode"]:
            raise ValueError("New versionCode must exceed source/published APK")
        iteration = CONFIG.get("iteration")
        if iteration and sha(apk.read_bytes()) == iteration["base"]["apkSha256"]:
            payload = content(apk, iteration["base"])
            # The trusted base and reviewed local definition form content before APK compilation.
            import sys
            sys.path.insert(0, str(ROOT / "tools"))
            from export_development import export_from_base
            payload = export_from_base(payload, iteration["provenance"], CONFIG)
        else:
            payload = content(apk)
        # Verify the complete target BEFORE touching any existing assets.
        destination.mkdir(parents=True, exist_ok=True)
        existing = [p for p in destination.rglob("*") if p.is_file()]
        if any(p.relative_to(destination).as_posix() not in payload for p in existing):
            raise ValueError("Existing export contains extra files; refusing to overwrite")
        for name, data in payload.items():
            path = destination / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        print(json.dumps(dict(sourceVersionCode=info["versionCode"], contentVersion=CONFIG["contentVersion"], manifestSha256=CONFIG["manifestSha256"], files=len(payload))))


def receipt(apk, output, code=None, name=None):
    info = verify_apk(apk, release=True)
    if code is not None and (info["versionCode"] != code or info["versionName"] != name):
        raise ValueError("Built version differs from requested version")
    files = content(apk)
    info.update(sha256=sha(apk.read_bytes()), sizeBytes=apk.stat().st_size,
                contentVersion=CONFIG["contentVersion"], contentHash=CONFIG["manifestSha256"], contentFiles=len(files))
    output.write_text(json.dumps(info, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(info))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["restore", "verify", "upgrade"])
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--old-apk", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--code", type=int)
    parser.add_argument("--name")
    parser.add_argument("--base-only",action="store_true",help="Verify/restore the reviewed iteration base, without exporting target content")
    args = parser.parse_args()
    if args.base_only:
        if args.mode not in ("restore","verify") or not CONFIG.get("iteration"):
            parser.error('base-only requires a configured iteration and restore/verify')
        base=CONFIG['iteration']['base']
        if not args.apk or sha(args.apk.read_bytes())!=base['apkSha256']:
            raise ValueError('Wrong reviewed base APK bytes')
        CONFIG.update(base)
        CONFIG.pop('iteration')
    if args.mode == "restore":
        restore(args.apk, next_code=args.code)
    elif args.mode == "verify":
        receipt(args.apk, args.output, args.code, args.name)
    else:
        old, new = verify_apk(args.old_apk), verify_apk(args.apk)
        if new["versionCode"] <= old["versionCode"]:
            raise ValueError("Upgrade versionCode must increase")
        print(json.dumps(dict(oldVersionCode=old["versionCode"], newVersionCode=new["versionCode"], compatible=True)))


if __name__ == "__main__":
    main()
