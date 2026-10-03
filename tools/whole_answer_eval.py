"""Prepare blinded paired grading and summarize independently entered utility scores.
No remote inference is performed. Supply phone exports and separately collected baseline answers.
"""
import argparse
import csv
import hashlib
import json
import random
import statistics
from pathlib import Path

def records(path):
    result={}
    for line in Path(path).read_text().splitlines():
        if not line.strip(): continue
        item=json.loads(line);question=item['question'].strip()
        if question in result: raise ValueError('Duplicate question; select one frozen run: '+question)
        result[question]=item
    return result

def prepare(questions,offline,baseline,output):
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    raw=Path(questions).read_bytes()
    queries=[line.strip() for line in raw.decode().splitlines() if line.strip() and not line.startswith('#')]
    if len(queries)<100 or len(set(queries))!=len(queries): raise ValueError('Use at least 100 unique, independently frozen questions')
    phone=records(offline);frontier=records(baseline)
    if set(phone)!=set(queries) or set(frontier)!=set(queries): raise ValueError('Both runs must contain every frozen question exactly once, including failures')
    rng=random.SystemRandom();order=list(queries);rng.shuffle(order)
    mapping=[];blind=[]
    for n,question in enumerate(order,1):
        answers=[('offline',phone[question]),('baseline',frontier[question])];rng.shuffle(answers)
        pair=f'q{n:03d}';mapping.append(dict(pair_id=pair,question=question,A=answers[0][0],B=answers[1][0]))
        blind.append(dict(pair_id=pair,question=question,A=answers[0][1].get('answer','[No answer]'),B=answers[1][1].get('answer','[No answer]')))
    (output/'blind-answers.json').write_text(json.dumps(blind,indent=2,ensure_ascii=False)+'\n')
    (output/'mapping-private.json').write_text(json.dumps(dict(question_sha256=hashlib.sha256(raw).hexdigest(),pairs=mapping),indent=2)+'\n')
    with (output/'grades.csv').open('w',newline='') as stream:
        writer=csv.writer(stream);writer.writerow(['pair_id','A_utility','B_utility','notes'])
        writer.writerows((item['pair_id'],'','','') for item in mapping)
    print('Give only blind-answers.json and grades.csv to the independent grader. Keep mapping-private.json hidden until grades are frozen.')

def summarize(mapping,grades,output):
    data=json.loads(Path(mapping).read_text());pairs={item['pair_id']:item for item in data['pairs']}
    scored={}
    with Path(grades).open(newline='') as stream:
        for row in csv.DictReader(stream):
            key=row['pair_id']
            if key not in pairs or key in scored: raise ValueError('Unknown or duplicate grading row: '+key)
            a=float(row['A_utility']);b=float(row['B_utility'])
            if not(0<=a<=4 and 0<=b<=4): raise ValueError('Utility scores must be 0..4 and finite')
            scored[key]={pairs[key]['A']:a,pairs[key]['B']:b}
    if set(scored)!=set(pairs): raise ValueError('Every question needs independent grades; omissions cannot be dropped')
    values=list(scored.values());offline=sum(v['offline'] for v in values);baseline=sum(v['baseline'] for v in values)
    if baseline==0: raise ValueError('A zero-utility baseline cannot establish the quality bar')
    ratios=[];rng=random.Random(20261003)
    for _ in range(10000):
        sample=rng.choices(values,k=len(values));den=sum(v['baseline'] for v in sample)
        if den: ratios.append(sum(v['offline'] for v in sample)/den)
    ratios.sort();ratio=offline/baseline
    report=dict(questions=len(values),question_sha256=data['question_sha256'],offline_mean_utility=offline/len(values),baseline_mean_utility=baseline/len(values),utility_ratio=ratio,
        paired_bootstrap_95_percent_interval=[ratios[int(len(ratios)*.025)],ratios[min(len(ratios)-1,int(len(ratios)*.975))]],
        observed_above_half=ratio>.5,note='Independent whole-answer scores only. This ratio does not certify device compliance, usability, or bounty acceptance.')
    Path(output).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);commands=parser.add_subparsers(dest='action',required=True)
    p=commands.add_parser('prepare');p.add_argument('questions');p.add_argument('offline');p.add_argument('baseline');p.add_argument('output')
    p=commands.add_parser('summarize');p.add_argument('mapping');p.add_argument('grades');p.add_argument('output')
    args=vars(parser.parse_args());action=args.pop('action');globals()[action](**args)
