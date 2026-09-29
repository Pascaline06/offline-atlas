# Evaluation protocol (preliminary)

## Exploratory retrieval probe, 2026-09-28

Thirty hand-chosen science, history and route questions were run against the installed September pack using the Android candidate SQL and Java passage scorer on a desktop. This was a development diagnostic; it includes previously tested questions and did not run the phone model or compare answers with a frontier baseline. It must not be quoted as a benchmark result.

The 0.1.24 changes moved generic vaccine, earthquake and tide explanations to the corresponding main articles, and eliminated observed irrelevant matches for an airplane mechanism question (a flight ban page) and a computer processor question (a text editor). Route endpoints now anchor the query; the Tokyo-to-Kyoto train question selected a Kyoto guide passage naming the Shinkansen from Tokyo. The same probe still withheld useful answers about volcanoes, stars and ocean salinity, and returned questionable passages for several historical causes. The next evaluation must grade the *entire generated answer* against its cited passage, including failures and latency on the target device.

Use a held-out set of at least 100 natural questions sampled across travel, history, science, health, and practical comparisons. Include at least 25 place-specific queries in cities on several continents; rotate queries to prevent tailoring to a single demo. Assess the *whole answer*, not whether a document was retrieved. For each question record the question, expected evidence, offline answer, source links and snapshot dates, frontier+internet comparison answer, latency to first useful text, total latency, peak resident memory, and manual factual grade. An independently graded result above half the baseline utility is required to support the bounty's quality claim. Publish the rubric and failures alongside successes.

Travel checks should distinguish an explicit dietary tag, independently verified menu, current opening hours, proximity, and recommendation quality. A missing tag means unknown; never infer vegan status from a name or cuisine. Offline data cannot guarantee current hours. Failure to find a city's venues must be reported plainly.
# Observed Samsung S10+ offline device test (2026-09-26)

- Version 0.1.5 retrieved the exact Machu Picchu and Angkor Wat source articles and displayed a cited two-source quick comparison while in airplane mode.
- Its Qwen3-4B-Instruct-2507 Q4_K_M model generated only the fragment `Machu Picchu and Angkor Wat` before the 120-second timeout. This does **not** meet the usable-speed or model-answer-quality target.
- Version 0.1.6 enables architecture-specific Arm CPU backends and rejects incomplete output; this version requires a fresh real-device timing measurement before drawing a speed conclusion.
- Version 0.1.6 produced an offline model answer with citations, but it stopped mid-sentence at the 128-token cap.
- Version 0.1.7 displayed the source comparison immediately and finished model generation in approximately 15 seconds on the Samsung S10+, by the tester's timing. Its validation rejected the output, so it supplied no usable model answer. The rejection reason and raw generated text were not recorded; do not infer which check failed.
- Pack-only QA found that a general query about the Roman Empire ranked a movie and a book ahead of the article, London travel search included London (Ontario), and vegan venue coverage for Lagos and Nairobi was empty. Version 0.1.8 addresses the first two retrieval ranking issues; coverage and model quality still fail the release gate.
- Version 0.1.9 changes native session reset and answer review. Unit cases test cited answers, hallucinated numbers including numbers with unit suffixes, missing comparison citations, and incomplete trailing sentences. Android build success is not evidence of a corrected model answer; retest the real-phone query and record whether the answer passed, its exact content, elapsed time, and whether a retry occurred.
- Version 0.1.9 did produce a two-sentence cited comparison on the Samsung S10+. The model described Angkor Wat as Hindu-Buddhist despite the prompt's truncated excerpt ending before its Buddhist history; valid citation numbers did not establish claim-level support. The Simple Wikipedia Machu Picchu article includes a claimed occupation starting 1200 AD alongside fifteenth-century construction, unlike UNESCO's account of fifteenth-century construction. Version 0.1.10 prefers the alternative local travel guide when it has the exact title and lengthens the comparison evidence, but claim-level citation accuracy remains unverified.
- Database check: 322,440 articles, 588 positive vegan-tagged venues; 440 from Portugal, 146 from Japan, one from Nigeria and one from Spain. Lagos, Nigeria had zero matched vegan-tagged venues, while Lagos, Portugal had 20. The tester's specialist travel question still fails for Nigeria and many other cities. Do not claim broad coverage.
- Version 0.1.10 chose the Wikivoyage Machu Picchu guide and produced a complete, broadly relevant comparison citing both articles on the S10+. The model added a third comparative sentence without citations. Version 0.1.11 checks every sentence and discards uncited trailing text. Its quality and latency still need device measurement; two successful examples cannot establish the requested >50% baseline.
- Version 0.1.11 produced a two-source model response on the S10+ with no uncited final sentence, but the two statements do little explicit synthesis. An offline desktop spot-check of 22 research/travel queries showed weak pair retrieval for solar versus wind power and photosynthesis versus respiration, plus zero tagged vegan venues for several major cities. Version 0.1.12 parses explicit two-subject questions and seeks separate articles for both. It avoids model generation on partial comparison sources. It does not resolve sparse global vegan venues or independent factual verification.
- Version 0.1.17 retrieved Solar panel and Wind turbine in 49 ms on the Samsung S10+ while in airplane mode, and the model provided a short, directly contrasted, cited answer about electricity generation. Model generation latency was not measured in the screenshots. One correct comparison is insufficient to claim broader quality.
- Version 0.1.18 searches structured restaurant listings in the existing Wikivoyage pack if OSM has no explicit vegan tags or unverified name/cuisine leads. A local pack check extracted named vegan leads in London, Tokyo, Berlin, and Lisbon; it found none in Lagos, Nigeria. It excludes closed listings and malformed stitched excerpts and displays listing edit dates when available. Travel answer quality still requires device checks and independently verified current menus, hours, and recommendations.
- Version 0.1.18 returned London dining leads in 414 ms on a Samsung S10+ offline, preserving source links and edit dates. Screenshots exposed a food cart, grocery shop, and multi-screen metadata as usability and category errors. Version 0.1.19 excludes clearly nonrestaurant listings and collapses repeated provenance behind a per-result tap target; the Android build cannot establish on-device layout correctness.
- Version 0.1.19 returned London dining leads in about 532 ms on the Samsung S10+ in the next airplane-mode check. The tester reported immediate display. Listings remain historical leads, with no current-menu verification or meaningful ranking.
- Unreleased 0.1.20 source was checked on the existing 2.4 GB database using the same FTS candidate order and Java passage scorer. In nine exploratory prompts, the highest-ranked covered passages discussed sky scattering, airplane lift, immune memory, causes of the 2008 financial crisis, Tokyo-to-Kyoto Shinkansen, Roman decline, and vaccine-induced immunity. The Soviet collapse query was withheld because the selected guide paragraph mentioned the collapse without explaining its causes; the solar-power-at-night query was also withheld. Candidate retrieval and Java cases were measured on a desktop, not on the Samsung. This is a development spot check, not a held-out quality score. Some other retrieved leads are irrelevant and the coverage heuristic cannot prove the model's claims follow from the passages. A signed Android build, phone latency and factual review are still required.
- Follow-up source check caught an overly broad table-cleanup change: it removed the vaccine evidence from an adaptive-immunity article. The cleaner now retains encyclopedic wikitables, while the passage scorer prefers the rare vaccine term over a generic sentence. This restored that source passage in desktop retrieval. No Android APK or phone result has been produced for this source revision.
# Development corpus probe

Run `python3 tools/probe_research.py /path/to/atlas.db
data/eval-development-questions.txt --output development-results.json` and
repeat with `data/eval-exploratory-questions.txt`. The script reproduces the
article candidate query and calls the same Java passage selection and evidence
check as the app. It requires Python 3 with SQLite FTS4 and Java 17 with the
`jdk.compiler` module. Inspect each excerpt and title in the JSON; a nonempty
result can still be misleading. The questions were used during development and
are not a held-out test or a measure of answer accuracy.

The app's travel venue route, comparisons, local model generation, and citation
review are outside this probe. A submission assessment still needs a held-out
question set, side-by-side judgments against internet plus a frontier model,
latency and peak RAM measurements on compatible GrapheneOS Pixel hardware,
offline installation evidence, and checks of pack coverage and cited claims.

## On-device answer records

Version 0.1.26 saves each search locally as one JSON object per line. The
**Export test results** button uses Android's document picker to save a copy.
Each record includes the displayed answer, full excerpts, source titles and
snapshot dates, retrieval time, first model text time, total time, and sampled
process PSS in KiB. A missing first-text value means no model text arrived.
The memory sampler runs every 500 ms during generation, so the value is an
estimate and may miss a brief peak. Do not mistake the presence of a citation
for support of every claim. Grade answers against their excerpts and an
independently researched baseline; keep failures and refusals in the sample.

Two v0.1.26 S10+ exports showed complete answers with citation [1]. The
airplane query took 41.4 seconds in total (1.9 seconds retrieval, 11.0 seconds
to first model text); the Soviet-collapse query took 27.5 seconds (6.6 seconds
retrieval, 8.2 seconds to first text). Sampled process PSS was approximately
3.1 GiB for both. The airplane answer added an effect of lift beyond the
excerpt's wording; the Soviet answer added public dissatisfaction and a causal
link between media exposure and dissolution that the selected excerpt did not
state. Version 0.1.27 rejects observed unsupported causal connectors and
narrows irrelevant mechanism context. These two examples are diagnostic, not a
held-out quality or target-device performance result.

In the next v0.1.27 phone run, two airplane attempts each failed with
"uncited sentence before sourced claims" despite first model text within
about 5–6 seconds; both returned no accepted model answer in 24–27 seconds.
The Soviet query passed on the second trial in 47.1 seconds, but its two cited
sentences mainly restated glasnost and media exposure rather than directly
explaining the collapse. Version 0.1.28 records unverified drafts in the
user-exported local file and provides an exact cited source extract after a
model failure. The extract must be distinguished from a generated answer in
quality grading.

The two v0.1.28 airplane exports included the same rejected drafts. The first
draft was a relevant sentence with its citation after the period (`lift. [1].`);
the retry placed `[1]` before its factual claim. Version 0.1.29 normalizes
only the former punctuation placement and tests the actual first draft with
the exported excerpt. Its UI shows the exact cited source passage before model
generation finishes, keeping a useful result visible if review rejects the
model. This is a targeted check, not evidence of general answer correctness.

Version 0.1.29 on the Samsung S10+ accepted the airplane answer in 12.0 seconds
(2.1 seconds retrieval, 5.2 seconds to first model text, about 3.1 GiB sampled
process PSS). The answer said only that forward motion produces lift over the
wings. It did not explain propulsion or why the wing experiences upward force.
This passes citation formatting but fails the intended answer-quality standard.

The installed 2.4 GB pack includes separate Airplane and Lift (force) passages
for thrust and downward air deflection, plus a dissolution article discussing
economic difficulty and republic departures. Pending 0.1.30 source selection
puts these directly in the evidence and requires both dimensions in the two
observed question types. Local source extraction was checked against the actual
pack; the Android model's new answer has not yet been measured on a phone.

The development probe against that pack selected a covered passage in 21/30
development and 23/70 exploratory queries. This is only a retrieval diagnostic:
it does not grade model output and includes no independent internet baseline.
Even this first gate is too weak for a >50% utility claim. Do not distribute
this source checkpoint as a validated quality release or submit the bounty.

The next retrieval checkpoint also checks the exact local subject article after
the FTS candidate limit; this recovered the Ice article instead of only ice
cream float titles. Recognizing “launched” for “launch” recovered the Rocket
mechanism. The development probe still selected 21/30 passages; exploratory
coverage rose from 23/70 to 25/70. Candidate coverage remains a generous upper
bound on answer utility and cannot substitute for grading model responses.

A subsequent route correction prefers sentences naming both endpoints and rail
travel. It accepts an explicit direct-train connection regardless of which
endpoint is named first, while still rejecting a reversed one-way bus passage.
The exact pack probe recovered the Delhi–Agra and Rome–Florence train passages,
raising exploratory passage selection to 27/70 and the combined development
set to 48/100. These are development prompts used in tuning, not held-out
answers or a bounty score.

## Frozen research/route retrieval audit, 2026-09-29

Before running the new probe, 50 distinct research and rail-route questions
were committed in `data/eval-holdout-research-20260929.txt`. This is a smaller
diagnostic than the planned 100-question whole-answer evaluation. The first
run selected 7/50 passages; the 1918 timeline was plainly unrelated, while
the Busan/West subpage gave only an indirect rail clue. A source check found
that the Busan main guide explicitly said KTX trains connect Seoul to Busan:
the filter accepted `train` but excluded plural `trains`. The fix selects the
main guide and rejects an Osaka airport passage incorrectly used as an Osaka
city-to-Kyoto train route. It also rejects the unrelated year timeline.

The final source checkpoint selected 6/50 passages. Manual inspection judged
only 3 as directly useful evidence: microwave heating, salt lowering the
freezing point, and the Seoul–Busan KTX connection. Three selected snippets
are poor: Barometer states what it measures without a mechanism, North
Magnetic Pole says a compass does **not** always point north, and Seed explains
dormancy rather than germination. Forty-four questions have no selected
passage. This inspection is not an independent factual baseline, full-answer
grade, model timing test, or statistical estimate of a random query stream.
The development probe now selects 20/30 plus 25/70 (45/100), down from 48
because weak causal matches and an airport-based route were rejected.

**Decision:** Do not submit the app against a >50% utility target on this
evidence. Current source coverage and passage precision fail before model
generation. A new or substantially expanded corpus, robust source selection,
then whole-answer grading against a frontier-plus-internet baseline are
needed. A GrapheneOS-compatible Pixel measurement remains outstanding.
