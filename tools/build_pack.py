"""Download documented public sources before use and build a versioned offline pack.

This is a desktop/CI preparation tool; it is never invoked by the Android app.
Replay an exact build with --manifest pointing to its source-inputs.json.
"""
import argparse
import datetime as dt
import hashlib
import json
import re
import shutil
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from build_index import build
from import_cirrus import convert

ROOT=Path(__file__).resolve().parents[1]


def html(url):
    with urllib.request.urlopen(url,timeout=30) as response:
        return response.read(1_000_000).decode('utf-8')


def discover(project):
    for base in ['https://dumps.wikimedia.org/other/cirrus_search_index/',
                 'https://dumps.wikimedia.org/other/cirrussearch/']:
        try:
            dates=sorted(set(re.findall(r'\b(20\d{6})/?',html(base))),reverse=True)
            for date in dates[:4]:
                directory=base+date+'/'
                listing=html(directory)
                urls=[urllib.parse.urljoin(directory,value) for value in re.findall(r'href="([^"]+)"',listing)
                      if project in value and 'content' in value and re.search(r'\.json\.(?:gz|bz2)$',value)]
                if not urls and 'cirrus_search_index' in base:
                    nested=directory+'index_name='+project+'_content/'
                    try:
                        urls=[urllib.parse.urljoin(nested,value) for value in re.findall(r'href="([^"]+)"',html(nested))
                              if project in value and 'content' in value and re.search(r'\.json\.(?:gz|bz2)$',value)]
                    except urllib.error.HTTPError:
                        pass
                if urls:
                    return sorted(urls)[0],date
        except (urllib.error.URLError,UnicodeDecodeError):
            continue
    raise RuntimeError('Cannot discover a public '+project+' content dump. Supply --manifest with available versioned source URLs.')


def download(url,path,expected=None):
    temp=path.with_suffix(path.suffix+'.download')
    digest=hashlib.sha256();size=0
    try:
        with urllib.request.urlopen(url,timeout=90) as response,temp.open('wb') as out:
            while True:
                block=response.read(1024*1024)
                if not block:
                    break
                size+=len(block)
                if size>8_000_000_000 or shutil.disk_usage(path.parent).free<512_000_000:
                    raise RuntimeError('Source download exceeds disk safety budget')
                digest.update(block);out.write(block)
        actual=digest.hexdigest()
        if expected and expected!=actual:
            raise RuntimeError('Source checksum mismatch: '+url)
        temp.replace(path)
        return actual,size
    finally:
        temp.unlink(missing_ok=True)


def sha256(path):
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):
            digest.update(block)
    return digest.hexdigest()


def run(output,manifest=None):
    output.mkdir(parents=True,exist_ok=True)
    inputs=[] if manifest is None else json.loads(manifest.read_text())['sources']
    sources=[];documents=[]
    if not inputs:
        try:
            inputs=[dict(project=project,url=url,snapshot=date,format='cirrus')
                    for project in ['simplewiki','enwikivoyage']
                    for url,date in [discover(project)]]
        except RuntimeError as error:
            print(str(error), 'Using versioned Wikipedia mirror instead.', flush=True)
            base='https://huggingface.co/datasets/wikimedia/wikipedia/resolve/main/'
            inputs=[dict(project='simplewiki',url=base+'20231101.simple/train-00000-of-00001.parquet',
                         snapshot='20231101',format='parquet'),
                    dict(project='enwiki',url=base+'20231101.en/train-00000-of-00041.parquet',
                         snapshot='20231101',format='parquet')]
    for index,entry in enumerate(inputs):
        project=entry['project'];url=entry['url'];date=entry['snapshot'].replace('-','')
        format_=entry.get('format','cirrus')
        raw=output/(str(index)+('.parquet' if format_=='parquet' else '.json.gz' if url.endswith('.gz') else '.json.bz2'))
        print('Downloading',project,url,flush=True)
        checksum,size=download(url,raw,entry.get('sha256'))
        target=output/(str(index)+'.jsonl')
        cap=entry.get('maximum_raw_article_characters',250000 if format_=='cirrus' else 60000)
        if format_=='parquet':
            from import_parquet import convert_parquet
            count=convert_parquet(raw,target,project,date,cap)
            license_='CC BY-SA 3.0 and GFDL as declared by the wikimedia/wikipedia dataset card; Wikimedia contributors, revision history at each article URL'
        else:
            count=convert(raw,target,project,date,cap)
            license_='CC BY-SA 4.0; Wikimedia contributors, revision history at each article URL'
        if count<1000:
            raise RuntimeError('Unexpectedly small '+project+' corpus: '+str(count))
        sources.append(dict(project=project,url=url,sha256=checksum,bytes=size,format=format_,
            snapshot=str(dt.datetime.strptime(date,'%Y%m%d').date()),articles=count,
            license=license_,maximum_raw_article_characters=cap))
        documents.append(target)
        raw.unlink()
    (output/'places.jsonl').write_text('')
    database=output/'atlas.db'
    print('Building article and passage indexes',flush=True)
    build(documents,output/'places.jsonl',database)
    for path in documents:
        path.unlink()
    import sqlite3
    with sqlite3.connect(database) as con:
        metadata=dict(con.execute('SELECT key,value FROM pack_metadata'))
    if int(metadata['fixture_count'])!=0:
        raise RuntimeError('Production pack contains fixtures')
    if database.stat().st_size+2_497_281_120>49_000_000_000:
        raise RuntimeError('Pack and supported model exceed the installed asset budget')
    manifest_data=dict(schema=1,prepared_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
        sources=sources,pack=dict(file='atlas.db',sha256=sha256(database),bytes=database.stat().st_size,
        **metadata),scope='Versioned article sources listed above. English Wikipedia mirror is one of 41 shards, not the full encyclopedia. No live or verified-current venues.')
    (output/'source-inputs.json').write_text(json.dumps(manifest_data,indent=2)+'\n')
    archive=output/'offline-atlas-knowledge.zip'
    print('Compressing installation pack',flush=True)
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=True) as zip_:
        zip_.write(database,'atlas.db')
    artifacts=[archive,output/'source-inputs.json']
    # GitHub release assets have a 2 GiB/file limit. The app joins numbered parts.
    if archive.stat().st_size>1_900_000_000:
        artifacts.remove(archive)
        with archive.open('rb') as stream:
            part=1
            while True:
                chunk=stream.read(900_000_000)
                if not chunk:
                    break
                path=Path(str(archive)+'.part'+str(part).zfill(2))
                path.write_bytes(chunk);artifacts.append(path);part+=1
        archive.unlink()
    (output/'SHA256SUMS').write_text(''.join(sha256(path)+'  '+path.name+'\n' for path in artifacts))
    print(json.dumps(manifest_data['pack'],indent=2),flush=True)
    return database


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--manifest',type=Path)
    args=parser.parse_args()
    run(args.output,args.manifest)
