"""Bounded audio QA for the four already-selected sources; not a second ROM importer."""
import hashlib,json,wave,subprocess
from pathlib import Path
import numpy as np
import imageio_ffmpeg

ROOT=Path(__file__).resolve().parents[1]
def features(x):
    frames=np.lib.stride_tricks.sliding_window_view(x,1024)[::200]
    z=np.log1p(abs(np.fft.rfft(frames*np.hanning(1024),axis=1))[:,2:450])
    z-=z.mean(axis=1,keepdims=True)
    return z/np.maximum(np.linalg.norm(z,axis=1,keepdims=True),1e-10)
def main():
    data=json.loads((ROOT/'game-data/provenance/audio-log01.json').read_text(encoding='utf-8'))
    result=[]
    for a in data['assets']:
        src=a['source'];recording=ROOT/src['recording']
        with wave.open(str(recording)) as f:
            assert f.getframerate()==48000 and f.getnchannels()==1
            x=np.frombuffer(f.readframes(f.getnframes()),'<i2').astype(float)[::12]
        start,end=src['searchWindowSeconds'];query=features(x[int(start*4000):int(min(end,start+6)*4000)])
        raw=subprocess.check_output([imageio_ffmpeg.get_ffmpeg_exe(),'-v','error','-i',src['path'],'-f','f32le','-ac','1','-ar','4000','pipe:1'])
        ref=features(np.frombuffer(raw,'<f4'))
        scores=[float(np.mean(np.sum(ref[i:i+len(query)]*query,axis=1))) for i in range(len(ref)-len(query))]
        best=int(np.argmax(scores));duration=len(raw)/16000
        assert duration*1000>a['loopEndMs'] and max(scores)>.4
        result.append({'id':a['id'],'decoder':'FFmpeg 7.1','durationSeconds':duration,
                       'targetRomRecordingSha256':hashlib.sha256(recording.read_bytes()).hexdigest(),
                       'sourceSha256':hashlib.sha256(Path(src['path']).read_bytes()).hexdigest(),
                       'rmsOriginal':float(np.sqrt(np.mean(x*x))), 'bestSpectralSimilarity':max(scores),
                       'referenceOffsetSeconds':best*.05,'confidence':'HIGH',
                       'limitation':'Spectral similarity is supporting musical evidence, not byte identity or automatic VERIFIED promotion.'})
    (ROOT/'reports/audio-source-check.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
