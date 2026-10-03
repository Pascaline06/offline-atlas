# Development validation results, 2026-10-03

These are desktop diagnostics and build checks, not Android measurements, a held-out quality benchmark or a bounty compliance claim.

## Complete answers

[Run 37159012633](https://github.com/Pascaline06/offline-atlas/actions/runs/37159012633), revision `06625f38de55bff5a186e39b908c84451eae33b1`, tested 12 development questions with the exact public knowledge pack and pinned model. Its downloadable artifact contains complete outputs and source excerpts.

Of 11 stable questions, **zero produced an accepted source-checked answer**: nine fell back to uncited model knowledge, and two timed out without a final answer. A twelfth query requesting live restaurant information was correctly refused. Seven source generation attempts and three verification attempts timed out; one comparison lacked evidence for both subjects. Median elapsed time for the 11 stable questions, including errors, was 74.26 seconds on the two-thread desktop server.

These results motivated the separate Quick answer and Research sources actions. Quick answer avoids source generation and verification, but its actual phone speed remains unmeasured. The probe predates the later verifier wording and Android cancellation fixes, and HTTP timeout behavior differs from JNI. Its results must not be presented as a phone benchmark or as evidence that the current research mode is effective.

Some recovered model answers also contain errors: an oversimplified assertion that tides everywhere produce two high/low tides each day, and a convergent-volcano explanation that says a subducting plate melts instead of explaining water-driven mantle melting. Retrieval occasionally ranks unrelated meanings above the relevant article. These are unresolved quality limitations, not acceptable substitutes for independent whole-answer grading.

## Source-check regression

[Run 37160007681](https://github.com/Pascaline06/offline-atlas/actions/runs/37160007681) produced correctly formatted verdicts for all 12 cases but only **11 correct decisions**. It falsely accepted an invalid existential-to-individual inference. The subsequent revision strengthens the entailment instruction, limits verification to passages actually cited by the answer, and makes incorrect or malformed fixture verdicts fail CI. Removing uncited passages reduces needless verification context; it does not establish usable speed. [Run 37160995231](https://github.com/Pascaline06/offline-atlas/actions/runs/37160995231) still falsely accepted that inference despite the stronger wording and therefore failed CI. The next revision requires a bounded evidence assessment before the verdict and strictly parses both fields. Consult its latest pinned-model check for actual results; a format or prompt change is not proof of a fix.

The local checker can still make semantic mistakes. Passing these development fixtures does not certify factual accuracy or establish research utility.

## Build and corpus

[Android run 37160005523](https://github.com/Pascaline06/offline-atlas/actions/runs/37160005523) passed 27 Python checks, 12 Java suites, C++ Unicode conversion behavior, three Kotlin native-operation tests, APK/test APK compilation, the expected signing certificate and the absence of Internet permission. These are build checks; physical-device instrumentation has not run.

The published real pack contains 393,457 articles and 831,678 passages, no fictional fixtures and no places. Its 2023-11-01 snapshot includes Simple English Wikipedia and one of 41 English Wikipedia shards. Database plus supported model occupies 7,037,738,080 bytes before APK, cache, logs and retained downloads.

## Remaining evidence

Follow [the requirement matrix](BOUNTY-STATUS.md), [device protocol](DEVICE-VALIDATION.md) and [whole-answer evaluation](WHOLE-ANSWER-EVAL.md). Real compatible Android/GrapheneOS operation, <=12 GB memory, total installed storage, useful latency, independently graded >50% research utility and genuine public demo/poidh proof remain open. Do not describe this preview as a completed bounty submission.
