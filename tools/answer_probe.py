"""Development-only desktop probe using shared retrieval, prompts and answer review.
Server/tokenizer scheduling differs from Android JNI. This is not phone proof or a held-out quality score.
"""
import base64
import json
import re
import sqlite3
import subprocess
import sys
import tempfile
import time
import urllib.error
from pathlib import Path
from model_pilot import request
from retrieve_passages import Retriever,encode
ROOT=Path(__file__).resolve().parents[1]
QUERIES=['Why is the sky red at sunset?','How do antibiotics and vaccines differ?','Why do some bacteria become resistant to antibiotics?','How does a solar cell turn light into electricity?','How do tides form?','Why did the Soviet Union break apart?','How do boiling and filtration differ in making water safer?','How does plate tectonics explain both earthquakes and volcanoes?','Why does a pressure cooker cook food faster?','How does inflation affect borrowers and savers differently?','Why is correlation insufficient to establish causation?','Name three currently open vegan restaurants in Lagos, Nigeria, with confirmed menus and hours.']
def run(database,model,binary,output):
    server=subprocess.Popen([binary,'serve','-m',model,'--host','127.0.0.1','--port','18889','-c','4096','-t','2','-ngl','0'],stdout=subprocess.DEVNULL,stderr=subprocess.STDOUT)
    try:
        for _ in range(180):
            if server.poll() is not None: raise RuntimeError('Model server exited')
            try:
                if request('http://127.0.0.1:18889/health').get('status')=='ok': break
            except (urllib.error.URLError,TimeoutError,ValueError): pass
            time.sleep(1)
        else: raise TimeoutError('Model initialization')
        with tempfile.TemporaryDirectory() as temp,Retriever() as retrieval,sqlite3.connect(f'file:{database}?mode=ro',uri=True) as con:
            source=ROOT/'app/src/main/java/org/offlineatlas'
            subprocess.run(['java','--module','jdk.compiler/com.sun.tools.javac.Main','-d',temp,str(source/'PromptPolicy.java'),str(source/'AnswerReview.java'),str(source/'JsonClaims.java'),str(ROOT/'tools/AnswerBridge.java')],check=True)
            def prompt(action,*args):
                values=[encode(s) for s in args]
                if action=="answer": values.append(str(bool(retrieval.subjects(args[0]))).lower())
                return base64.b64decode(subprocess.check_output(['java','-cp',temp,'org.offlineatlas.PromptPolicy',action,*values])).decode()
            system=prompt('system')
            def generate(user,limit,grammar="",timeout=90):
                result=request('http://127.0.0.1:18889/v1/chat/completions',dict(messages=[dict(role='system',content=system),dict(role='user',content=user)],temperature=0,max_tokens=limit,stream=False,grammar=grammar),timeout=max(1,timeout))
                return result['choices'][0]['message']['content']
            def tokens(user): return len(request('http://127.0.0.1:18889/tokenize',dict(content=user),timeout=30)['tokens'])
            def review(draft,evidence,comparison=False):
                values=subprocess.check_output(['java','-cp',temp,'org.offlineatlas.AnswerBridge',encode(draft),encode(evidence),str(comparison).lower()]).decode().split('\t')
                return values[0]=='1',base64.b64decode(values[1]).decode(),base64.b64decode(values[2]).decode()
            with Path(output).open('w') as stream:
                for question in QUERIES:
                    start=time.monotonic();row=dict(question=question,scope='desktop development probe, not an Android or quality benchmark')
                    try:
                        if 'currently open' in question:
                            row.update(answer='I cannot verify live information offline.',mode='live_refusal')
                        else:
                            pair=retrieval.subjects(question)
                            comparison=bool(pair)
                            passages=retrieval.retrieve(con,question)
                            evidence='\n\n'.join(f'[{i}] {p["title"]}\n{p["excerpt"]}' for i,p in enumerate(passages,1))
                            while tokens(prompt('answer',question,evidence))>1400 and evidence:
                                blocks=re.split(r'(?m)(?=^\[\d+\] )',evidence);evidence=''.join(blocks[:-1]).strip()
                            if comparison and len(passages)<2: evidence=""
                            row['sources']=passages;row['model_context']=evidence
                            source_start=time.monotonic()
                            try:
                                draft=generate(prompt('answer',question,evidence),256,prompt('grammar',evidence) if evidence else '',timeout=55)
                                accepted,text,reason=review(draft,evidence,comparison and bool(evidence))
                            except TimeoutError:
                                draft='[Source attempt timed out]';accepted=False;text='';reason='source timeout'
                            row['source_draft']=draft;row['structure_reason']=reason
                            if accepted and evidence:
                                check=prompt('verify',evidence,text)
                                try: verdict=generate(check,8,prompt('verdict_grammar'),timeout=55-(time.monotonic()-source_start)).strip().upper() if tokens(check)<=2400 else 'CONTEXT_OVERFLOW'
                                except TimeoutError: verdict='CHECK_TIMEOUT'
                                row['verdict']=verdict;accepted=verdict=='SUPPORTED'
                            if accepted:
                                row.update(answer=text,mode='source_checked' if evidence else 'model_knowledge')
                            else:
                                draft=generate(prompt('answer',question,''),256,timeout=90-(time.monotonic()-start));accepted,text,reason=review(draft,'')
                                row.update(answer=text if accepted else '[No accepted answer]',mode='model_knowledge_after_source_rejection',knowledge_draft=draft)
                    except Exception as error: row['error']=repr(error)
                    row['seconds']=round(time.monotonic()-start,2)
                    stream.write(json.dumps(row,ensure_ascii=False)+'\n');stream.flush();print(question,row.get('mode'),row.get('error','ok'),flush=True)
    finally:
        server.terminate()
        try: server.wait(timeout=10)
        except subprocess.TimeoutExpired: server.kill()
if __name__=='__main__':run(*sys.argv[1:])
