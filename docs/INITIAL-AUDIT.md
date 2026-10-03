# Offline Atlas assessment and two-day delivery plan

Initial review, before v0.2 changes, 2026-10-03. The source checkout/history/native archives were subsequently recovered through a Git bundle. See README for current behavior.

Verdict: substantial Android plumbing exists, but the research product is far below the bounty's quality bar. This is a salvageable prototype, not a halfway-complete competitive submission.

## What was actually reviewed

- Public repository: https://github.com/Pascaline06/offline-atlas
- `main`: `2f191a88fede69ea611cc3ea5e1e885fe4a9d969`, materialized at `/workspace/offline-atlas`.
- Newer draft PR #1: `codex/research-results-filter-v0.1.21`, commit `b7b6b02163839b1e21e0944013bbd0e7c4b56238`, materialized at `/workspace/offline-atlas-review`. Despite the branch name, its app version is 0.1.34.
- Direct Git cloning failed because the sandbox could not reach its configured proxy; permission-gated retries stalled and were cancelled. The alternative used the connected GitHub API. These directories are source snapshots, **not Git clones**. They lack Git history and the seven native dependency archive parts; those parts DO exist in the upstream repository.
- All 67 draft text files match their Git blob hashes. The checked-in SQLite database was retrieved separately. Its integrity check passes; it contains only **2 fictional articles and 3 fictional places**.
- Production model weights and the real 2.4 GB knowledge pack were not downloaded. Corpus statistics and device observations below come from repository records, not new measurements here.

Local verification: all 21 Python index/import/retrieval tests passed. Eight Java suites passed: AnswerReview, ComparisonQuery, WikiText, ResearchEvidence, EvidenceFallback, PhoneRegression, AnswerCompleteness, and VoyageListings. The environment has Java's compiler module but no `javac` launcher; compilation used `java --module jdk.compiler/com.sun.tools.javac.Main`.

No local APK build or new device inference was performed: Android SDK, NDK, Gradle, native archives, production assets, and a physical phone are unavailable in this snapshot environment. The latest draft's Android CI run succeeded and produced a signed Preview update APK artifact: https://github.com/Pascaline06/offline-atlas/actions/runs/36576073837 . The pinned model pilot also succeeded: https://github.com/Pascaline06/offline-atlas/actions/runs/36576084003 . CI completion does not establish answer quality.

## What Work got right

The app has a real Java/Kotlin Android shell, an adapted llama.cpp JNI inference engine, streaming output, SQLite FTS4 retrieval, local model and pack import, source attribution, checksums for the recommended model, exportable device evaluation logs, and a CI build. The manifest requests no INTERNET permission; reviewed dependencies show no Play Services requirement. Those are valuable foundations.

The recommended model is Qwen3-4B-Instruct-2507 Q4_K_M, 2,497,281,120 bytes, with a recorded SHA-256. The database builder validates provenance and writes atomically. Venue tags are treated as evidence rather than proof of current menus. The project documentation honestly reports failures and does not claim it has won the bounty. Keep these parts.

## What is wrong, in priority order

### P0: Useful answers are blocked before the model can help

The repository's frozen 100-question audit reports 18 selected passages: 6 directly relevant, 4 partial, 8 wrong, and 82 abstentions. This is a **passage audit**, not 6 correct generated answers, not a random-query estimate, and not a score against frontier models. It nevertheless exposes a severe retrieval problem.

`AtlasRepository.java:309` retrieves at most 16 candidates per query attempt, using title heuristics and broad term matching. `ResearchEvidence.java:51` then applies extensive hand-written title, verb, and causal-pattern rules. Unapproved candidates are discarded. `MainActivity.java:182` prevents model generation when no approved result exists. There is no general local-model fallback.

The resulting experience is often a refusal despite related material being present. More isolated regex fixes will continue trading false answers against false refusals. Measure title discovery, passage retrieval, evidence relevance, and final answer quality separately.

### P0: The general synthesis path barely has evidence to synthesize

`MainActivity.java:185` permits two sources for comparisons and two named demo questions. Other research questions receive just one source (`:215`), capped at 900 characters (`:219`). Two-source passages are capped at 700 characters each. Before passage scoring, the cleaned article is capped at 11,000 characters in `AtlasRepository.java:326`.

The system prompt demands evidence-only answers under 100 words. This can serve short supported lookups, but it severely limits explanation, multi-source synthesis, and reasoning. The model documentation says “up to four” passages; the actual UI generation path sends one or two. Documentation and implementation disagree.

### P0: Citation review is not factual verification

`AnswerReview.java` checks citation markers, some numbers, punctuation, contrast words, and literal causal connector overlap. It does not determine whether cited passages support each claim.

Two extra local probes against the actual class reproduced both failure modes:

- Evidence: `[1] The Earth orbits the Sun.` Answer: `The Earth is made entirely of chocolate [1].` Result: **accepted**.
- Evidence: `[1] Opening the valve enables water to flow through the pipe.` Answer: `Opening the valve allows water to flow through the pipe [1].` Result: **rejected**, because `allows` is absent from the evidence.

Keep structural checks, but stop treating their success as evidence verification. A local model critique can assist, but cannot certify its own accuracy either. Ground answers with precise passages and assess claim support independently.

### P1: Repairs are overfitted to observed demonstrations

`AnswerCompleteness.java:10` and `:14` recognize two exact question forms: airplane flight and Soviet collapse. `AtlasRepository.java:383` and `:403` inject special articles for these questions. These are legitimate regressions to preserve, but they are not a general completeness or research strategy. New phrasing and other mechanisms do not receive the same treatment.

The source-probe implementation in Python also duplicates Android candidate retrieval. It excludes comparisons, venue routing, and model generation; its deduplication behavior is not identical to the Android path. Keep it as a diagnostic, not the authoritative end-to-end benchmark.

### P1: Real data exists in prior work, but outsiders cannot install it from the repo in minutes

Repository records describe a 2.4 GB pack containing 322,440 articles. The checked-in asset is only the fictional starter database. README installation steps refer to “supplied” APK and three pack parts without direct download links. The GitHub releases endpoint returned no releases at review time. A signed CI artifact exists, but it is not a durable, prominently linked public release with its matching knowledge pack.

Raw-data rebuild scripts and manifests help developers. They do not replace a working prebuilt pack for a phone user. Several manifest URLs are mutable; a recorded hash cannot make an old snapshot downloadable again. Publish the actual versioned pack with hashes, provenance, licenses, and stable links.

There is no Gradle wrapper or top-level application license in the reviewed source tree. Build tools are manually specified; vendored native source needs an extraction step. Add the wrapper and a consistent release process, preserve upstream licenses, and move the long README version history to a changelog.

### P1: Compatible-device performance and resource compliance remain unproven

Prior Samsung S10+ records include ~3.1 GiB sampled process PSS, answer times of 27.5–47.1 seconds, and an accepted but incomplete airplane answer in 12.0 seconds. These are selected observations on earlier versions, not the current version's distribution of usable answer times. Process PSS is not whole-device memory, and a 500 ms sampler can miss peaks.

There is no recorded current-version GrapheneOS-compatible Pixel validation. Establish cold and warm latency, long-session memory, thermal behavior, and airplane-mode operation on actual compatible hardware. Do not infer these from desktop model pilots.

The known pack plus recommended model suggests a modest installed asset footprint, but the 50 GB requirement has not been fully accounted for. Imports copy external downloads into private storage; replacements temporarily hold old and incoming files. Existing size guards consider mainly model and pack sizes, not every asset, log, duplicate installation, or temporary file. Add free-space checks and publish measured final and installation-peak storage.

### P2: Travel specialization has consumed effort while general research still fails

The recorded 588 positive vegan-tagged venues are mostly Portugal and Japan; only one is in Nigeria. Historical Wikivoyage leads improve coverage but cannot establish current businesses, menus, or opening hours. Keep travel as one evaluation category. Spend this sprint on the general research path rather than adding more restaurant-specific parsing.

## Bounty readiness

| Requirement | Evidence and current judgment |
| --- | --- |
| Real Android execution | Prior Samsung execution recorded; current draft has successful Android CI. Current physical-device behavior needs validation. |
| Compatible GrapheneOS hardware | Outstanding physical Pixel validation. |
| Entirely offline; no runtime networking | Strong source design: no INTERNET permission, local JNI and SQLite, no Play Services core dependency. Confirm on the installed artifact. |
| Maximum 12 GB RAM | Selected older app PSS observations are promising; target-device and whole-environment accounting remain outstanding. |
| Maximum 50 GB total assets | Likely ample headroom for the recommended assets, but full installed/temporary-storage accounting is missing. |
| Explanation, comparison, synthesis, reasoning | Some successful examples exist; general retrieval and synthesis are poor. |
| Usable lookup speeds | Source results can be fast; accepted, complete generated answers are not yet characterized on the target phone. |
| Public code and documented resources | Public source, model manifest, and import scripts exist. Matching public APK/pack release and concise installation instructions are missing. |
| More than half of frontier-plus-internet usefulness | No whole-answer baseline comparison exists. The current source audit is deeply unfavorable. |
| Public demo and submission proof | No proof links found in the reviewed repository. Prepare only after artifact and device gates pass. |

## Architecture for the next two days

Keep the Android app, local llama.cpp, the pinned 4B model, and SQLite. A dense quantized model plus improved local retrieval is within the allowed architecture. An extreme MoE or custom n-gram model is a separate research project and is not a credible two-day completion path.

Build paragraph/section-level retrieval with stable IDs and provenance. Use token-aware context selection across two to four relevant passages. Add FTS ranking using a compatible FTS4 rank implementation, title/phrase boosts, and generic entity normalization. Benchmark a bounded candidate pool, initially around 64, against memory and latency. Query expansion or an embedding/reranking model is an experiment only if simpler retrieval still fails and device budgets permit it.

Provide grounded answers when sources support them. Evaluate an explicitly labeled local-model general-knowledge mode for stable topics when retrieval fails; do not attach invented citations, and refuse unsupported current/local claims. Compare that mode with grounded-only behavior on development data before enabling it. It must run in-process, with no API or localhost-server dependency in the Android app.

Show sources quickly, then a cancellable streamed answer. Retain honest uncertainty and useful partial answers. Do not remove evidence requirements simply to manufacture a higher acceptance rate.

## Day 1: Make the core useful and reproducible

Assume a focused engineering day of roughly 10 hours; some downloads and builds can run unattended.

| Time | Work | Concrete exit criterion |
| --- | --- | --- |
| Hours 0–2 | Recover the actual production pack and signed APK; freeze base commit, model hash, and pack hash. Confirm access to a compatible Pixel. Add wrapper/build instructions and asset download manifest. Reserve a genuinely new final evaluation set before tuning. | A new tester has exact files and commands; matching pack can be searched; target device is identified. If the pack is lost, rebuilding it becomes the critical path. |
| Hours 2–6 | Add generic paragraph/section retrieval and ranking. Replace hard title/causal gates with scored evidence selection and calibrated abstention. Use known questions only as development regressions. | On the production corpus, evaluate candidate recall and relevant-passage precision separately, including new paraphrases. No claim that a retrieval hit is a correct answer. |
| Hours 6–9 | Feed multiple passages within a token budget; remove the two-demo restriction from the general path. Repair structural review and evaluate the optional general-knowledge mode. Add cancellation and preserve source visibility. | Complete answers to explanation, comparison, and synthesis examples; no fabricated citations; rejection reasons and exact context are logged. |
| Hours 9–10 | Build a signed installable candidate and run 15–20 diverse phone smoke queries in airplane mode. Check restart and saved-asset loading. | A real-device export captures answers, timing, failures, and sampled memory for this exact candidate. |

## Day 2: Prove the behavior and prepare a release

| Time | Work | Concrete exit criterion |
| --- | --- | --- |
| Hours 0–3 | Measure cold/warm startup, retrieval, first useful answer text, total latency, memory, thermal degradation, and storage on the compatible Pixel. Fix the largest bottleneck only. Test model/pack replacement failure, low storage, restart, and interruption. | No crashes or loss of the last working assets; measured compliance within 12 GB RAM and 50 GB assets. Record OS/device details. |
| Hours 3–7 | Freeze the candidate. Run a new 100-question whole-answer set; compare against a documented frontier-plus-internet baseline and a small 1B local baseline where practical. Grade correctness, relevance, completeness, useful reasoning, source support, and freshness. Count refusals and failures. | Full transcripts and scoring rubric; report offline/baseline utility ratio and uncertainty. Model-only mode, extracts, and generated grounded answers remain separately identifiable. |
| Hours 7–9 | Fix only release blockers. Publish versioned signed APK, pack, manifests, checksums, license notices, and concise install instructions. Re-run a fresh install with someone following only the README. | Public artifacts match the tested commit; a fresh user can install after downloading without substantial debugging. Any code change affecting behavior requires focused retesting. |
| Hours 9–10 | Record an uninterrupted offline demo with several unseen, substantive questions and visible device/version context. Prepare X/Farcaster text and the poidh screenshot/link package. | Evidence corresponds to the exact public functional version. Posting and submitting are separate user-authorized actions. |

Proposed engineering targets, not bounty-defined numbers: useful source text within 2 seconds warm; first useful model text within 8 seconds; typical short answers within 20 seconds. Record median and p95, cold starts, and retry costs. Treat these as targets to assess, not promises.

The final evaluation must be independent of tuning. The existing 100-question audit is now known and can be used for diagnosis, but is no longer a fresh final test. Publish selection methodology and results honestly; a raw usefulness ratio alone does not establish Vitalik's subjective bar. Build the online comparison outside the offline Android app; use independently supplied baseline answers or a separately authorized research workflow.

## The realistic commitment

Two days can plausibly deliver a substantially better, installable, measured prototype if the existing production assets and compatible phone are available. Two days cannot guarantee a bounty-winning result. If the held-out full-answer evaluation still falls below the target, release it as an honest beta and continue improving it rather than submit a quality claim.

The first implementation priority is **generic passage retrieval plus sufficient multi-source context**. The first logistical priority is **recovering and publishing the matching real pack**. UI polish comes after these. The next review should judge exported answers from the current phone build, not how many versions or tests were added.
