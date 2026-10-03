"""Stream the public wikimedia/wikipedia mirror; requires pyarrow on desktop only."""
import datetime as dt
import json
from pathlib import Path

def convert_parquet(source, output, project, snapshot, max_chars=60000):
    import pyarrow.parquet as pq
    if project not in {'simplewiki','enwiki'}:
        raise ValueError('Unsupported mirror project')
    date=str(dt.datetime.strptime(snapshot,'%Y%m%d').date())
    count=0
    with Path(output).open('w',encoding='utf-8') as target:
        for batch in pq.ParquetFile(source).iter_batches(batch_size=128,columns=['title','text','url']):
            for row in batch.to_pylist():
                title=row['title'];body=row['text']
                if not title or not body or len(body.strip())<80:
                    continue
                item=dict(id=project+':'+title,title=title,body=body[:max_chars],source=row['url'],
                    source_date=date,license='CC BY-SA 3.0 and GFDL (wikimedia/wikipedia dataset card); Wikimedia contributor attribution and full revision history at source URL')
                target.write(json.dumps(item,ensure_ascii=False)+'\n');count+=1
    return count
