"""Corpus retrieval using the exact Android Java plan, SQL, and ranker."""
import base64
import json
import sqlite3
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def encode(value):
    return base64.b64encode(value.encode()).decode()


class Retriever:
    def __init__(self):
        self.directory = tempfile.TemporaryDirectory()
        source = ROOT / 'app/src/main/java/org/offlineatlas'
        subprocess.run(['java', '--module', 'jdk.compiler/com.sun.tools.javac.Main',
                        '-d', self.directory.name, str(source/'RetrievalPlan.java'),
                        str(source/'WikiText.java'),str(source/'ComparisonQuery.java'), str(ROOT/'tools/RetrievalBridge.java')], check=True)
        self.process = subprocess.Popen(['java', '-cp', self.directory.name, 'org.offlineatlas.RetrievalBridge'],
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)

    def line(self, *values):
        self.process.stdin.write('\t'.join(values)+'\n')
        self.process.stdin.flush()
        value = self.process.stdout.readline()
        if not value:
            raise RuntimeError('Retrieval bridge exited unexpectedly')
        return value.strip()

    def subjects(self,question):
        count=int(self.line('C',encode(question)))
        return [base64.b64decode(self.process.stdout.readline()).decode() for _ in range(count)]

    def retrieve(self, con, question, comparison=True):
        pair=self.subjects(question) if comparison else []
        if pair:
            left=self.retrieve(con,pair[0],False);right=self.retrieve(con,pair[1],False)
            selected=left[:1]
            if right and not any(p['source']==right[0]['source'] and p['excerpt']==right[0]['excerpt'] for p in selected): selected.append(right[0])
            return selected
        count = int(self.line('P', encode(question)))
        queries = [base64.b64decode(self.process.stdout.readline()).decode() for _ in range(count)]
        version = con.execute('PRAGMA user_version').fetchone()[0]
        passages = version >= 5
        sql = base64.b64decode(self.line('Q', '1' if passages else '0')).decode()
        found, seen = [], set()
        for query in queries:
            for identity, title, text, origin, date, license_, ordinal, matchinfo in con.execute(sql, (query,)):
                key = (identity, ordinal)
                if key in seen:
                    continue
                seen.add(key)
                score, coverage, excerpt = self.line('S', encode(question), encode(title), encode(text),
                    base64.b64encode(matchinfo).decode(), '1' if passages else '0').split('\t')
                if float(coverage) == 0:
                    continue
                found.append(dict(id=identity, ordinal=ordinal, title=title, source=origin,
                    source_date=date, license=license_, score=float(score), coverage=float(coverage),
                    excerpt=base64.b64decode(excerpt).decode()))
        found.sort(key=lambda item: -item['score'])
        selected, per_document, text_seen = [], {}, set()
        for item in found:
            origin = item['source']
            if per_document.get(origin, 0) >= 2 or item['excerpt'] in text_seen:
                continue
            selected.append(item)
            text_seen.add(item['excerpt'])
            per_document[origin] = per_document.get(origin, 0)+1
            if len(selected) == 4:
                break
        return selected

    def close(self):
        self.process.stdin.close()
        try:
            if self.process.wait(timeout=5):
                raise RuntimeError('Retrieval bridge failed')
        finally:
            if self.process.poll() is None:
                self.process.kill()
                self.process.wait()
            self.directory.cleanup()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('database', type=Path)
    parser.add_argument('questions', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    records = []
    with sqlite3.connect(f'file:{args.database}?mode=ro', uri=True) as con, Retriever() as retriever:
        for question in args.questions.read_text().splitlines():
            if question.strip() and not question.startswith('#'):
                records.append(dict(question=question, passages=retriever.retrieve(con, question)))
    args.output.write_text(json.dumps(records, ensure_ascii=False, indent=2)+'\n')
    print(f'{sum(bool(r["passages"]) for r in records)}/{len(records)} retrieved; relevance and answers require grading.')
