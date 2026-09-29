#!/usr/bin/env python3
"""Reproduce article candidate retrieval and evidence ranking on an atlas.db pack.

This is a development diagnostic, not an answer-quality benchmark. It emits
the first covered article and a citation excerpt for human review. It does not
run the language model, travel planner, or comparisons.
"""
import argparse
import base64
import json
import re
import sqlite3
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STOP = set("the and what why how tell about compare best are for from with causes caused cause work works does did was were have has had its this that main way get stay into between can you me to in of is at happen happening occur occurs begin become".split())
ROUTE = re.compile(r"\bfrom\s+(.+?)\s+to\s+(.+?)(?:\s+by\s+(?:train|bus|ferry|plane|air))?[?.!]?\s*$",re.I)


def terms(question):
    route = ROUTE.search(question)
    subject = " ".join(route.groups()) if route else question
    return list(dict.fromkeys(w for w in re.findall(r"[^\W_]+",subject.lower())
                              if len(w)>=3 and w not in STOP))[:10]


def candidates(con, question):
    base=terms(question)
    keywords=[]
    for word in base:
        if word in keywords or len(keywords)>=12: continue
        keywords.append(word)
        singular=None
        if len(word)>5 and word.endswith('ies'): singular=word[:-3]+'y'
        elif len(word)>4 and word.endswith('oes'): singular=word[:-2]
        elif len(word)>4 and word.endswith('s') and not word.endswith('ss'): singular=word[:-1]
        if singular and singular not in keywords: keywords.append(singular)
        for form in {'launch':('launched','launching')}.get(word,()):
            if form not in keywords and len(keywords)<12: keywords.append(form)
        if word=='collapse': keywords.extend(x for x in ('dissolution','dissolved','breakup') if x not in keywords)
    if not keywords: return []
    expression=' OR '.join('"'+w+'"' for w in keywords)
    anchors='"'+base[0]+'" AND "'+base[-1]+'"' if len(base)>=2 else expression
    if len(base)>=2 and base[-1]=='collapse':
        anchors='"'+base[0]+'" AND ("collapse" OR "dissolution" OR "dissolved" OR "breakup")'
    exact_phrases=[keywords[i]+' '+keywords[i+1] for i in range(len(keywords)-1)]
    priority=' + '.join('CASE WHEN instr(lower(d.title), ?) > 0 THEN 1 ELSE 0 END' for _ in keywords[:12])
    placeholders=lambda n: ','.join('?' for _ in range(n))
    exact='CASE WHEN lower(d.title) IN ('+placeholders(len(keywords))+') THEN 0 ELSE 1 END,'
    if exact_phrases:
        exact+='CASE WHEN lower(d.title) IN ('+placeholders(len(exact_phrases))+') THEN 0 ELSE 1 END,'
    source="CASE WHEN d.id LIKE 'enwikivoyage:%' THEN 0 ELSE 1 END" if ROUTE.search(question) else "CASE WHEN d.id LIKE 'simplewiki:%' THEN 0 ELSE 1 END"
    sql=f"SELECT d.title,d.body,d.source FROM doc_search JOIN documents d ON d.rowid=doc_search.rowid WHERE doc_search MATCH ? ORDER BY {exact} ({priority}) DESC, CASE WHEN lower(d.title) LIKE '%(movie)%' OR lower(d.title) LIKE '%(film)%' THEN 1 ELSE 0 END, {source}, length(d.title), d.title COLLATE NOCASE LIMIT 16"
    params=keywords+exact_phrases+keywords[:12]
    seen={}
    for query in dict.fromkeys([anchors,expression]):
        for title,body,origin in con.execute(sql,[query]+params):
            seen.setdefault(title,(title,body,origin))
    if base and not ROUTE.search(question):
        subject=base[0]
        singular=subject[:-1] if len(subject)>4 and subject.endswith('s') and not subject.endswith('ss') else subject
        for title,body,origin in con.execute("SELECT title,body,source FROM documents WHERE lower(id)=lower(?) OR lower(id)=lower(?) LIMIT 6",
                                             ('simplewiki:'+subject,'simplewiki:'+singular)):
            seen.setdefault(title,(title,body,origin))
    return list(seen.values())


def encoded(value): return base64.b64encode(value.encode()).decode()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('database',type=Path)
    parser.add_argument('questions',type=Path)
    parser.add_argument('--output',type=Path)
    options=parser.parse_args()
    with tempfile.TemporaryDirectory() as temp:
        classes=Path(temp)
        subprocess.run(['java','--module','jdk.compiler/com.sun.tools.javac.Main','-d',str(classes),
            *map(str,[ROOT/'app/src/main/java/org/offlineatlas/WikiText.java',
                      ROOT/'app/src/main/java/org/offlineatlas/ResearchEvidence.java',
                      ROOT/'tools/ResearchBatchProbe.java'])],check=True)
        java=subprocess.Popen(['java','-cp',str(classes),'org.offlineatlas.ResearchBatchProbe'],
                              stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
        results=[]
        with sqlite3.connect(f'file:{options.database}?mode=ro',uri=True) as con:
            for question in options.questions.read_text().splitlines():
                if not question.strip() or question.startswith('#'): continue
                found=[]
                for order,(title,body,source) in enumerate(candidates(con,question)):
                    java.stdin.write('\t'.join(map(encoded,(question,title,body)))+'\n')
                    java.stdin.flush()
                    covered,score,excerpt=java.stdout.readline().rstrip('\n').split('\t')
                    if covered=='1':
                        found.append(dict(title=title,source=source,score=int(score),order=order,
                                          excerpt=base64.b64decode(excerpt).decode()))
                found.sort(key=lambda item:(-item['score'],item['order']))
                result=dict(question=question,top=found[0] if found else None,
                            covered_count=len(found))
                results.append(result)
                print(f"{len(results):3d} {question} => {found[0]['title'] if found else '[abstain]'}",flush=True)
        java.stdin.close()
        if java.wait()!=0: raise RuntimeError('Java passage probe failed')
    if options.output: options.output.write_text(json.dumps(results,indent=2,ensure_ascii=False)+'\n')
    print(f"Covered {sum(item['top'] is not None for item in results)}/{len(results)}; this is retrieval coverage, not answer correctness.")


if __name__=='__main__': main()
