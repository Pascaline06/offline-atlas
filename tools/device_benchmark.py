"""Run frozen questions through the installed real Android UI over USB adb.
Install the app, test APK, model and pack first. Disconnect the phone's networking.
The host collects evidence over USB; the app itself never sends network requests.
"""
import argparse
import base64
import hashlib
import json
import subprocess
from pathlib import Path

def adb(*args,**kwargs):
    return subprocess.run(['adb',*args],check=True,**kwargs)
def run(questions,output):
    output.mkdir(parents=True,exist_ok=True);raw=questions.read_bytes()
    queries=[q.strip() for q in raw.decode().splitlines() if q.strip() and not q.startswith('#')]
    if len(queries)<100 or len(set(queries))!=len(queries): raise ValueError('Need at least 100 unique frozen questions')
    metadata=dict(question_sha256=hashlib.sha256(raw).hexdigest(),questions=len(queries),note='Record physical disconnected-use video and GrapheneOS device identity separately.')
    (output/'run.json').write_text(json.dumps(metadata,indent=2)+'\n')
    for filename,command in [('device-model.txt',['getprop','ro.product.model']),('device-build.txt',['getprop','ro.build.fingerprint']),('memory.txt',['cat','/proc/meminfo']),('radios.txt',['dumpsys','connectivity'])]:
        with (output/filename).open('wb') as out: adb('shell',*command,stdout=out)
    # Preserve previous observations before starting a fresh run; private app files only.
    previous=subprocess.run(['adb','shell','run-as','org.offlineatlas.preview','cat','files/evaluation.jsonl'],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL).stdout
    (output/'previous-evaluation.jsonl').write_bytes(previous)
    adb('shell','run-as','org.offlineatlas.preview','rm','-f','files/evaluation.jsonl','files/evaluation.jsonl.previous')
    encoded=base64.b64encode(raw).decode()
    with (output/'instrumentation.txt').open('wb') as out:
        adb('shell','am','instrument','-w','-e','class','org.offlineatlas.ResearchBenchmarkTest','-e','questions_base64',encoded,'org.offlineatlas.preview.test/androidx.test.runner.AndroidJUnitRunner',stdout=out)
    with (output/'phone-answers.jsonl').open('wb') as out: adb('shell','run-as','org.offlineatlas.preview','cat','files/evaluation.jsonl',stdout=out)
    logs=[json.loads(line) for line in (output/'phone-answers.jsonl').read_text().splitlines() if line.strip()]
    if len(logs)!=len(queries) or {r['question'] for r in logs}!=set(queries): raise RuntimeError('Incomplete run. Keep failures; do not grade a selected subset.')
    if 'OK (1 test)' not in (output/'instrumentation.txt').read_text(): raise RuntimeError('Instrumentation did not pass; inspect saved output')
    with (output/'final-memory.txt').open('wb') as out: adb('shell','dumpsys','meminfo','org.offlineatlas.preview',stdout=out)
    print(f'{len(logs)} complete observations saved. Independently grade full answers and verify device/RAM/storage/offline video.')
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('questions',type=Path);parser.add_argument('output',type=Path);args=parser.parse_args();run(args.questions,args.output)
