"""Pinned tokenizer data only; never loads model weights or remote Python code."""
import hashlib
import json
import urllib.request
from bridge import ROOT

revision = '851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a'
expected_sha256 = '5f9e4d4901a92b997e463c1f46055088b6cca5ca61a6522d1b9f64c4bb81cb42'
path = ROOT / '.local-ai/tokenizer.json'
if not path.exists():
    url = f'https://huggingface.co/Qwen/Qwen3.5-4B/resolve/{revision}/tokenizer.json'
    with urllib.request.urlopen(url, timeout=90) as response:
        data = response.read()
    json.loads(data)
    if hashlib.sha256(data).hexdigest() != expected_sha256:
        raise ValueError('Tokenizer checksum differs from the validated pinned revision')
    temp = path.with_suffix('.tmp')
    temp.write_bytes(data)
    temp.replace(path)
if hashlib.sha256(path.read_bytes()).hexdigest() != expected_sha256:
    raise ValueError('Existing tokenizer checksum mismatch; refusing to use it')
receipt = dict(repository='Qwen/Qwen3.5-4B', revision=revision, sha256=hashlib.sha256(path.read_bytes()).hexdigest(), bytes=path.stat().st_size)
(path.parent / 'tokenizer-receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
print(json.dumps(receipt))
