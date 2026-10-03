#!/usr/bin/env python3
"""Stream a Wikimedia CirrusSearch content shard into licensed article JSONL."""
import argparse
import bz2
import html
import json
import re
from pathlib import Path


def convert(source, output, project, snapshot, max_chars=250000):
    if project not in {'enwikivoyage', 'simplewiki', 'enwiki'}:
        raise ValueError('Unsupported Wikimedia project')
    if not re.fullmatch(r'\d{8}', snapshot):
        raise ValueError('Snapshot must be YYYYMMDD')
    if not 1000 <= max_chars <= 1000000:
        raise ValueError('max_chars must be between 1000 and 1000000')
    count = 0
    opener = bz2.open if str(source).endswith('.bz2') else open
    with opener(source, 'rt', encoding='utf-8', errors='replace') as stream, Path(output).open('w', encoding='utf-8') as target:
        for line in stream:
            try:
                item = json.loads(line)
            except json.JSONDecodeError:
                continue
            title = item.get('title')
            body = item.get('source_text') or item.get('text') or item.get('opening_text')
            if not isinstance(title, str) or not isinstance(body, str) or len(body.strip()) < 80:
                continue
            if item.get('namespace', 0) not in (0, '0'):
                continue
            body = html.unescape(re.sub(r'<[^>]+>', ' ', body))
            body = re.sub(r'\s+', ' ', body).strip()
            body = body[:max_chars]
            if len(body) < 80:
                continue
            slug = title.replace(' ', '_')
            host = {'enwikivoyage': 'en.wikivoyage.org', 'simplewiki': 'simple.wikipedia.org',
                    'enwiki': 'en.wikipedia.org'}[project]
            from urllib.parse import quote
            source_url = f'https://{host}/wiki/{quote(slug)}'
            entry = {'id': f'{project}:{title}', 'title': title, 'body': body,
                     'source': source_url, 'source_date': f'{snapshot[:4]}-{snapshot[4:6]}-{snapshot[6:]}',
                     'license': 'CC BY-SA 4.0; Wikimedia contributor attribution and full revision history at source URL'}
            target.write(json.dumps(entry, ensure_ascii=False) + '\n')
            count += 1
    return count


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('shard', help='Downloaded CirrusSearch *_content JSON/BZ2 shard')
    parser.add_argument('output')
    parser.add_argument('--project', required=True, choices=['enwikivoyage', 'simplewiki', 'enwiki'])
    parser.add_argument('--snapshot', required=True, help='Dump date as YYYYMMDD')
    parser.add_argument('--max-chars', type=int, default=250000)
    args = parser.parse_args()
    print(convert(args.shard, args.output, args.project, args.snapshot, args.max_chars))
