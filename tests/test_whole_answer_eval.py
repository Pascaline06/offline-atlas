import contextlib
import csv
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from whole_answer_eval import summarize
class WholeAnswerEvaluationTests(unittest.TestCase):
    def test_rejected_questions_remain_in_denominator(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);mapping=root/'mapping.json';grades=root/'grades.csv';output=root/'result.json'
            mapping.write_text(json.dumps(dict(question_sha256='frozen',pairs=[dict(pair_id='q1',A='offline',B='baseline'),dict(pair_id='q2',A='baseline',B='offline')])))
            grades.write_text('pair_id,A_utility,B_utility,notes\nq1,4,4,correct\nq2,4,0,no answer\n')
            with contextlib.redirect_stdout(io.StringIO()): summarize(mapping,grades,output)
            self.assertEqual(json.loads(output.read_text())['utility_ratio'],.5)
            self.assertFalse(json.loads(output.read_text())['observed_above_half'])
            grades.write_text('pair_id,A_utility,B_utility,notes\nq1,4,4,correct\n')
            with self.assertRaises(ValueError): summarize(mapping,grades,output)
