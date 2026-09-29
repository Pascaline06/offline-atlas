# Offline Atlas — Android device test build

Version 0.1.29 fixes the exact citation-formatting failure exported from the
Samsung S10+ in 0.1.28: the model wrote a supported airplane sentence followed
by `. [1].`, which the reviewer treated as an uncited sentence. The reviewer
now moves that same citation before the sentence's final punctuation and
checks the result normally. A regression case uses the device's actual draft
and source passage. For questions with a covered article, the app shows a
short cited extract immediately while the model works; it replaces that extract
only when the model response passes review. The extract is labeled as a source
passage, not a synthesized answer. Android compilation and regression tests
cannot by themselves certify model behavior across other questions.

Version 0.1.28 follows the Samsung S10+ v0.1.27 exports: two airplane model
attempts generated text but both failed the uncited-introduction check; the
Soviet answer passed on a retry yet remained a limited source recital. The
model prompt now asks for an immediate cited fact without an introduction.
If model review still fails, the app displays a clearly labeled extract of
the selected local source instead of leaving only an error. Test exports
include the rejection outcome and unverified draft text for diagnosis. An
extract is not a complete model answer. This remains a preview, not a bounty
submission.

Version 0.1.27 responds to two exported Samsung S10+ offline answers from
0.1.26. Both generated complete cited replies, but their final causal steps
went beyond the cited passages. Answer review now rejects explicit new causal
links absent from the supplied evidence and retries with stricter instructions.
The airplane mechanism excerpt stops before unrelated uses and war history.
Exported test records now include the app version and whether a model was used.
These guards cannot prove factual entailment; their effect on the phone must be
checked. This remains a development build, not a bounty submission.

Version 0.1.26 records each on-device search and its displayed answer in a
private, exportable test file. **Export test results** lets the tester choose
where to save the JSONL file; the app does not transmit it. Records include
the question, cited excerpts, source dates, retrieval and answer time, time to
first model text, and a sampled process memory maximum. These measurements
make a device assessment reviewable; sampled memory can miss a short peak.
For supported explanations, the model receives a longer selected passage and
is asked for a concise two- or three-sentence answer with citations. This
prompt change needs on-device factual and latency validation. Version 0.1.26
remains a preview and should not be submitted as a bounty claim.

Version 0.1.25 adds a reproducible 100-question development probe against the
2.4 GB knowledge pack. It improves cause and mechanism passage selection and
rejects several false matches: a consequence of the Berlin Wall's fall as its
cause, World War III for World War I, a route in the opposite direction, movie
titles, and travel pages that are not the destination. The probe reports
retrieval coverage and excerpts, not model answer correctness. See
`docs/EVAL.md` for how to run it and the remaining submission gates. This
version is still a preview, not a bounty-ready claim.

Version 0.1.24 is a retrieval preview. A desktop probe of 30 exploratory questions against the installed knowledge pack found that the previous broad FTS ordering missed general articles and sometimes accepted unrelated pages. The candidate query now considers exact one-word titles, recognizes common plural subjects, and requires stronger subject matching for explanatory questions. Route queries anchor on their origin and destination rather than generic words such as travel and train. The probe now selects the general Vaccine, Earthquake, and Tide articles, retrieves the Tokyo-to-Kyoto Shinkansen passage, and rejects the observed No fly list and Word processor mismatches. This is not a held-out quality score or a phone validation of this version. Some queries still abstain despite relevant articles; other historical causal matches remain too broad. Do not use it as a bounty claim.

**Status:** Running code and a real data pack are being validated. This is not yet a bounty claim: source-grounded answer quality, speed and peak memory on real Android and compatible Pixel hardware remain unmeasured, and the corpus does not reliably cover specialized vegan queries in every city.

## Install on a phone

1. On an Android 11+ arm64 phone, download the supplied debug APK and install it. Android may ask you to allow installs from your browser or file manager. A Samsung S10+ can be the first test device; GrapheneOS compatible Pixel testing is an additional release gate.
2. Download all three supplied files named `offline-atlas-knowledge-20260926.zip.part01`, `.part02`, and `.part03` into the same Downloads folder. In **Offline Atlas → Install knowledge pack**, long-press one part in the Android file picker, select the other two and confirm. The app orders and joins them while importing. If your file picker cannot select multiple files, join the parts in Termux with `cat ~/storage/downloads/offline-atlas-knowledge-20260926.zip.part0{1,2,3} > ~/storage/downloads/atlas.zip`, then select `atlas.zip` in the app. The initial bundled pack contains fictional test data only; replace it before real queries.
3. Download the 2.5 GB [recommended GGUF model](https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf). Open **Select local GGUF model** in the app and pick the downloaded file. The app verifies its SHA-256 against `data/model-manifest.json`, then loads it locally. Loading and first answer may take time on a phone.
4. Turn on airplane mode. Try `vegan restaurants in Porto`, `vegan restaurants in Tokyo`, and a comparison or explanation question. The answer and evidence must both appear with source dates. Source URLs are citations only; the app never opens them. Report the model output, time, and any false statement for each query.

If version 0.1.0 displayed `Model could not load: null` or 0.1.1 displayed `Native model loader returned 1`, install version 0.1.2 over the existing app without uninstalling it, then select the existing GGUF again. Version 0.1.2 extracts the CPU backend library so llama.cpp can discover it, reports loader errors and uses a 4096-token context and 256-token batch. Do not delete or download the model again when its checksum has already passed. Keep the phone charged during installation and inference.

Version 0.1.3 shows local source excerpts before generation, streams partial text and stops after two minutes. It also fixes a JNI token limit error that counted the user prompt twice, which could make a short answer generate hundreds of extra tokens. Native inference in the debug APK is compiled with optimizations, avoiding the slow unoptimized default. Install over an existing 0.1.2 installation; the stored database and model are retained.

Version 0.1.4 handles `Compare X and Y` with exact local article titles and cleans wiki markup from displayed evidence. This fixes the device test where `Wat` retrieved unrelated pages and the evidence came from image captions and reference lists. The two-title comparison route has been checked against the shipped index; broader reasoning and answer quality still need device evaluation.

Version 0.1.5 shows a cited, two-source comparison immediately, even when the phone model cannot produce a token within the two-minute budget. It sends shorter evidence to the local model to reduce prompt processing. This improves fallback behavior on the Samsung S10+ but does not establish that the model meets the speed or quality bar for the bounty.

Version 0.1.6 enables the CPU variants used by llama.cpp's official Android build, allowing runtime choice of architecture-specific kernels instead of forcing the baseline implementation. It also marks incomplete model output as a failure rather than presenting a fragment as an answer. Real-phone performance must still be measured.

Version 0.1.7 responds to a real offline test on a Samsung S10+: the local model generated a cited answer but hit its 128-token cap mid-comparison. This build asks for a shorter answer, allows up to 192 output tokens under the same two-minute limit, rejects incomplete or uncited final answers and numeric claims absent from the supplied excerpts, and collapses long source excerpts until tapped. The screenshot is evidence of generation working on that phone, not of adequate answer speed or bounty-level accuracy. Install over 0.1.6; the imported pack and model stay installed. A completed answer on this build still needs real-device validation.

Version 0.1.8 improves general topic retrieval by prioritizing exact two-word article titles and removing duplicate titles from the results. Travel guide retrieval confines London results to London and its subpages, excluding London (Ontario). These changes have index-level tests; the 0.1.7 phone test still rejected its generated comparison answer, so model-answer quality is unresolved. Do not treat this as a bounty-ready release.

Version 0.1.9 resets the model's token cache to the system prompt before each independent lookup. This fixes the prior behavior where a completion stopped by the token cap could pollute the next search. It reduces generic queries to one source, retries a rejected answer once with stricter instructions, keeps complete cited sentences when the token limit cuts off a trailing fragment, and shows the mechanical rejection reason. A user can tap to inspect a rejected draft marked unverified. The native build and automated review checks pass; actual answer acceptance and factual quality require a fresh offline phone test.

Version 0.1.10 addresses the source gap seen in the 0.1.9 device screenshots. For an exact-title comparison it prefers the Wikivoyage article over Simple Wikipedia when both are installed; the Machu Picchu Wikivoyage lead avoids a contradictory 1200 AD sentence in the other article. It supplies longer evidence for both comparison subjects and checks that century claims in the model answer appear in the supplied excerpts. Travel searches can specify a country by English name or ISO country code to disambiguate cities such as Lagos in Nigeria and Portugal. This does not establish that citations entail every claim or that the pack covers vegan venues in Nigeria. Test on the phone before drawing quality conclusions.

Version 0.1.11 follows the S10+ device result for 0.1.10: a complete answer cited both source articles, but it appended an uncited summary sentence. The answer review now removes an uncited final sentence and rejects uncited claims between sourced sentences. The prompt asks for a direct comparison without an unsupported closing claim. Wiki heading cleanup also removes stray punctuation in the quick summary. This is a mechanical citation check, not semantic proof that each claim is entailed by its cited article.

Version 0.1.12 follows a broader corpus spot-check. It recognizes `compare X and Y`, `X vs Y`, and `what is the difference between X and Y`, retrieves each subject separately, and expands shared nouns in pairs such as `solar and wind power`. A comparison requires two distinct local articles before generation; otherwise the app displays partial leads and says what is missing. The lookup also finds case-insensitive source IDs correctly in the existing pack, and new packs include an index for that query. Desktop corpus checks found distinct Solar energy/Wind power, Photosynthesis/Respiration and Tokyo/Kyoto articles. On-device speed and answer quality must still be tested. Global vegan venue coverage remains sparse.

Version 0.1.13 adds the missing lookup index to older installed packs. On the Samsung S10+ the solar/wind query retrieved its two articles in 60 ms, and local generation took around 10–12 seconds. Its answer still listed separate facts instead of explaining the difference.

Version 0.1.14 is a separate **Offline Atlas Preview** application (`org.offlineatlas.preview`). It asks the model for a direct contrast on one shared attribute and rejects a pair of unrelated facts as an answer to a comparison. Snapshot labels clarify that an article may contain older figures. The earlier 0.1.13 signing key was lost in a reset, so this preview cannot update the old installation or access its private database and model. Keep the installed 0.1.13 app. To try the preview with real data, import the locally retained knowledge-pack parts and GGUF model into the preview separately; this needs additional free storage. Do not delete or redownload your original pack and model files. Real-device answer quality for this preview is unverified.

Version 0.1.14 was distributed without its bundled starter index. On a clean install it shows `Index unavailable: atlas.db` and disables the import button; do not use that build. Version 0.1.15 generates and packages the starter index during Gradle builds, and CI checks that the APK contains it. Install 0.1.15 over the Preview app without uninstalling it. It leaves the separate 0.1.13 installation alone. The starter index is explicitly fictional test data and must be replaced with the real knowledge pack before factual evaluation.

Version 0.1.16 follows a real Samsung S10+ offline test of 0.1.15: retrieval took 52 ms, but the model copied source URLs and a snapshot date into the answer and put citations before its claims. The model input now omits URL and date metadata, asks for citations after supported claims, and retries responses that copy source metadata or cite before claims. Full provenance remains visible under the answer. This check does not establish factual correctness; the new build still needs real-device testing.

Version 0.1.17 improves evidence choice for comparisons of electrical power sources. When the offline corpus has an article about a panel, turbine or cell that discusses electricity, the app uses that article before a broader energy overview. In the shipped pack this changes the solar/wind pair from Solar energy and Wind power to Solar panel and Wind turbine; the former distinguishes photovoltaic electricity from solar heat. Nested wiki image captions are also removed before evidence is shown to the model. Other topics still fall back to exact titles and existing retrieval. Real-device quality remains to be measured.

Version 0.1.18 follows a Samsung S10+ airplane-mode check of 0.1.17: the solar/wind query retrieved the intended two articles in 49 ms and the model stated the electrical generation distinction with two citations. That is one correct short answer, not evidence of general research quality or a measured model latency. For vegan restaurant queries, when the pack lacks tagged OSM venues, the app now extracts named eating-place listings from locally stored Wikivoyage city guides, preserving their article URL, snapshot date, and available listing edit date. It skips closed listings and incomplete sections in the cached article. London, Tokyo, Berlin, and Lisbon have some named leads in the existing installed pack; Lagos, Nigeria still has none. Guide listings are older unverified leads, with no justified "best" ranking or current hours. The app does not ask the local model to embellish these travel leads.

Version 0.1.19 follows the London offline Samsung S10+ test of 0.1.18: the app surfaced named sourced listings in 414 ms but mixed a food cart and a grocery shop into restaurant results, and repeated verbose source metadata under every result. The guide parser now excludes these and bakeries from restaurant requests; OSM records for the same name and location are deduplicated. The travel UI shows compact entries with the neighborhood, summary, address, listing edit date, and an expandable source. Source articles and recorded dates are unchanged. Current business status and any "best" ranking still cannot be verified offline from the installed pack.

Version 0.1.20 is a **source checkpoint, not a distributed APK**. It adds question-specific passage selection for general research, searches both co-occurring terms and broader matches, ranks the local candidates by subject and passage, and withholds model generation unless the selected passage covers the question. It also rejects film and song pages as research evidence for historical subjects, and gives the model a longer selected excerpt. CI supports an optional private Preview signing key and verifies its certificate before publishing an update artifact; the signing key is not in this source archive. Desktop unit tests and real-corpus spot checks do not establish phone speed, source entailment, or Android build success. The installed Preview remains 0.1.19 until a signed 0.1.20 APK is built and tested.

The Android manifest requests **no INTERNET permission** and the code uses no Google Play Services. The source, model, and data files must be downloaded before going offline. Model and database are separate imports to keep the APK a reasonable size. Do not treat OSM tags or names as proof of current menus, hours, quality or which restaurant is best. When the pack lacks explicit dietary tags the app marks name/cuisine matches as unverified leads.

## Build from source

The source includes vendored llama.cpp at commit `2145525a4081d66ff1a87cf43ef809f95a85ac0c` and the adapted Android JNI example. Install JDK 17, Android SDK platform/build-tools 35, NDK `29.0.13113456`, CMake `3.31.6` and Gradle 8.10.2. With `ANDROID_HOME` set, run:

GitHub stores the pinned vendored source in seven numbered `third_party/llama-cpp-vendor.tar.gz.part*` files to keep the repository import manageable. Before a local build, extract it using `cat third_party/llama-cpp-vendor.tar.gz.part* | tar -xzf - -C third_party`. The GitHub Actions workflow performs this extraction automatically. The source ZIP offered separately already contains the extracted directory.

```sh
python3 -m unittest discover -s tests -v
gradle --no-daemon :app:assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow runs the same tests and build and rejects an INTERNET permission. There is no Google account or Play Store dependency.

**Updating an installed Preview:** Android requires the same signing certificate. Set `ATLAS_PREVIEW_KEYSTORE` to the private Preview keystore path and `ATLAS_PREVIEW_KEY_PASSWORD` to its password before building. Keep that keystore out of the public repository. The CI workflow publishes an installable Preview update artifact only when repository secrets `ATLAS_PREVIEW_KEYSTORE_B64` (base64 of the private keystore) and `ATLAS_PREVIEW_KEY_PASSWORD` are configured; it verifies the expected signing certificate. Without these secrets CI still checks and builds the code, but does not publish an APK that could be mistaken for an update. Do not uninstall the installed app just to try a differently signed CI build: uninstalling deletes the imported database and model.

## Rebuild the data pack

Python 3.10+ with SQLite FTS4 and `osmium==4.3.1` are required. The downloadable inputs and previously measured checksums are recorded in `data/source-manifest-20260920.json`. GeoNames `cities15000.zip` must be extracted to `cities15000.txt`. Verify mutable OSM URLs against the actual downloaded snapshot and record updated hashes. Example:

```sh
python3 tools/import_cirrus.py enwikivoyage_content-20260920-00000.json.bz2 voyage.jsonl --project enwikivoyage --snapshot 20260920
python3 tools/import_cirrus.py simplewiki_content-20260920-00000.json.bz2 simple.jsonl --project simplewiki --snapshot 20260920
python3 tools/import_osm_pbf.py portugal.osm.pbf cities15000.txt portugal.jsonl --snapshot 2026-09-26
python3 tools/import_osm_pbf.py tokyo.osm.pbf cities15000.txt tokyo.jsonl --snapshot 2026-09-26
python3 tools/import_osm_pbf.py nigeria.osm.pbf cities15000.txt nigeria.jsonl --snapshot 2026-09-26
python3 tools/build_index.py --documents voyage.jsonl simple.jsonl --places portugal.jsonl tokyo.jsonl nigeria.jsonl --cities cities15000.txt --output atlas.db
python3 tools/query_index.py atlas.db 'vegan restaurants in Tokyo'
```

The builder writes atomically and checks SQLite integrity. `docs/DATA.md` explains provenance, attribution and dietary evidence; `docs/EVAL.md` defines the missing quality comparison. See `docs/MODEL.md` for the model hash, inference path and limitations.

## Release gates

Publish a versioned APK and pack with checksums, demonstrate installation and offline operation on real hardware, measure speed/RAM/storage and failure cases, and compare a held-out research and travel set against internet plus frontier models. Public GitHub source and the exact submitted artifacts must be available when claiming the bounty. This checkpoint has not passed those gates.
