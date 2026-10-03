import json
import sqlite3
import tempfile
import unittest
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from build_index import build
from retrieve_passages import Retriever
from text_passages import passages,plain_text

class PassageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.retriever=Retriever()
    @classmethod
    def tearDownClass(cls):
        cls.retriever.close()

    def test_late_evidence_is_indexed_and_retrieved(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            body=('Historical introduction unrelated to the mechanism. '*500)+(
                'A pressure sensor uses a flexible diaphragm. Changes in pressure bend the diaphragm, '
                'changing the electrical resistance measured by a circuit. ')*4
            doc=dict(id='test:pressure',title='Pressure sensor',body=body,
                source='https://example.invalid/pressure',source_date='2026-01-01',license='TEST ONLY')
            (root/'docs.jsonl').write_text(json.dumps(doc)+'\n')
            (root/'places.jsonl').write_text('')
            build(root/'docs.jsonl',root/'places.jsonl',root/'atlas.db')
            with sqlite3.connect(root/'atlas.db') as con:
                rows=self.retriever.retrieve(con,'How does a pressure sensor use a diaphragm?')
                self.assertTrue(rows)
                self.assertIn('electrical resistance',rows[0]['excerpt'])
                self.assertEqual(rows[0]['source'],doc['source'])
                self.assertGreater(con.execute('select count(*) from passages').fetchone()[0],10)

    def test_nested_templates_and_refs_do_not_become_evidence(self):
        text=plain_text("{{nav|{{inner|noise}}}} '''Light''' scatters. <ref>irrelevant false claim</ref> [[Water|liquid water]] absorbs heat.")
        self.assertNotIn('noise',text)
        self.assertNotIn('false claim',text)
        self.assertIn('liquid water',text)

    def test_chunk_bound_and_complete_coverage(self):
        body=' '.join(f'word{i}' for i in range(4000))
        chunks=list(passages(body))
        self.assertTrue(all(len(chunk)<=1800 for chunk in chunks))
        self.assertTrue(all(f'word{i}' in ' '.join(chunks) for i in range(4000)))

    def test_empty_query_does_not_return_arbitrary_sources(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'atlas.db'
            build(ROOT/'data/sample_documents.jsonl',ROOT/'data/sample_places.jsonl',path)
            with sqlite3.connect(path) as con:
                self.assertEqual(self.retriever.retrieve(con,'how does it work?'),[])
