# Whole-answer usefulness evaluation

The bounty's quality bar refers to answers and useful research, not retrieval coverage or citation formatting. Use an independent, frozen set of at least 100 questions. Obtain full answers from the real offline phone, and separately from a named frontier model with internet search enabled. Record baseline model/version, date, search configuration and the source evidence. The Android app never accesses that service; baseline collection is a separate evaluator activity.

Save both runs as JSONL with `question` and `answer` fields, including failed/refused answers. Phone exports already use this format. Select one complete predetermined run; do not retain only the best retries.

```sh
python3 tools/whole_answer_eval.py prepare questions.txt phone-answers.jsonl frontier-answers.jsonl grading
```

Give `blind-answers.json` and `grades.csv` to independent human graders. Keep `mapping-private.json` hidden until grades are frozen. A knowledgeable grader should check facts against authoritative references and score how well each complete answer fulfills the question. Use this 0–4 utility rubric consistently:

- 0: absent, materially wrong or unsafe; fails the request.
- 1: a little correct information but major errors or gaps; weak usefulness.
- 2: partially useful with sound central content but meaningful omissions or reasoning gaps.
- 3: useful, accurate and well reasoned; only minor gaps.
- 4: accurate, complete, clear and directly useful for the request.

For a live-information query, an appropriate offline refusal can be honest but is generally less useful than a correct current answer; score usefulness rather than rewarding refusal automatically. Source-mode answers require correct claim support and citations. Knowledge-mode answers must not receive an invented-citation penalty if accurately labeled, but unsupported or wrong facts still lower utility. Include notes on mechanisms, synthesis, uncertainty, coverage, hallucinations and citation support. Prefer two independent graders and report disagreements.

```sh
python3 tools/whole_answer_eval.py summarize grading/mapping-private.json grading/grades.csv grading/report.json
```

The tool refuses missing grades and duplicate/missing questions, includes failures in the denominator, reports aggregate offline/baseline utility and a paired bootstrap interval. A ratio greater than 0.5 is evidence under this rubric; it is not a guarantee that the bounty judges agree. Report the interval and category breakdowns, and pair quality with latency/resource/device evidence.

The 100 development questions and 12-question desktop answer probe are development diagnostics. They cannot establish held-out quality or real-device compliance. The local model source checker can make errors; it is an additional rejection signal, not a truth certificate.
