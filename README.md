# Offline Atlas 0.2

An Android research assistant using a local Qwen3 4B model and a versioned SQLite passage index. The core app requests **no Internet permission**, needs no Google Play Services, and performs retrieval and inference in its own process. Download assets once before going offline.

This is a phone-validation preview. Android CI and host checks pass; current real Pixel/GrapheneOS operation, usable latency, peak RAM and the “more than half as useful as internet + frontier AI” target still require measured evidence. It is not a bounty claim.

## Install

Use an Android 11+ **arm64** phone. For bounty validation, use a compatible GrapheneOS Pixel with at most 12 GB RAM. Allow about **12 GB free storage** for downloads and private copies during installation.

1. Open [public releases](https://github.com/Pascaline06/offline-atlas/releases) and download `offline-atlas-0.2.0.apk` from the newest **phone-validation preview**. Install it. The separate tests APK is for USB validation, not normal use. APK checksums and the signing certificate are attached to the release.
2. Download [the real knowledge pack ZIP](https://github.com/Pascaline06/offline-atlas/releases/download/knowledge-v5-37155745133/offline-atlas-knowledge.zip) (1.45 GB). In the app, open **Assets and settings**, tap **Install knowledge pack** and choose that ZIP. Installation copies and validates its 4.54 GB database. The APK's tiny bundled database contains fictional test data; the app suppresses it as factual evidence.
3. Download [Qwen3-4B-Instruct-2507 Q4_K_M](https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf) (2.50 GB). In **Assets and settings**, tap **Add supported language model** and select it. The app checks the exact size and SHA-256 in [the model manifest](data/model-manifest.json). Keep the app open until loading finishes.
4. Enable airplane mode, disable Wi-Fi and disconnect other network transports. Use **Quick answer** for stable learned knowledge, or **Research sources** for a cited answer checked against retrieved passages. Model and pack restore after a restart. Original downloads can be deleted after successful import; the app uses its private copies.

The preparation pack contains **393,457 articles and 831,678 passages**, captured on **2023-11-01**. It combines Simple English Wikipedia and **one of 41 English Wikipedia shards**. It is not the complete English encyclopedia and contains no place database or Wikivoyage guides. Travel results therefore remain unavailable in this particular pack. [Source inputs, scope, licenses and database checksum](https://github.com/Pascaline06/offline-atlas/releases/download/knowledge-v5-37155745133/source-inputs.json) and [download checksums](https://github.com/Pascaline06/offline-atlas/releases/download/knowledge-v5-37155745133/SHA256SUMS) accompany it. Older packs with Wikivoyage/OSM remain importable.

## Research behavior

The app ranks overlapping passages using SQLite FTS4 and BM25, retrieves both sides of recognized comparisons, and sends up to four passages with numbered citations to the local model. Grounded output binds claims to valid source IDs through a decoding grammar. It checks answer structure and cited numbers, then asks the local model to check claim support. The local check can be wrong; source excerpts remain available for human inspection.

**Quick answer** generates from stable local model knowledge and lists retrieved passages separately for manual checking. **Research sources** sends eligible evidence to the model and checks support. The **Allow answers from local model knowledge without source support** switch permits the quick mode and knowledge recovery when research evidence is missing, incomplete, or rejected. These answers are explicitly labeled and have no source citations. Disable it for source-only use; both actions then use the evidence path. The app refuses recognized requests for live information, and never treats old venue records as verified current hours or menus.

Draft text is labeled unverified. **Stop answer** cancels generation. Attempts share a 90-second ceiling; a source attempt gets up to 55 seconds when knowledge recovery is enabled. A ceiling is not a usability claim. **Export test results** saves observations to a file you choose; nothing is uploaded.

Example development queries: “How do antibiotics and vaccines differ?”, “How does plate tectonics explain earthquakes and volcanoes?”, and “Why is correlation insufficient to establish causation?” Their usefulness must be judged from actual phone answers, not these examples or the presence of sources.

## Reproduce from source

Install a C++17 compiler, JDK 17, Python 3.11+, Android SDK 35, build-tools 35.0.0, NDK 29.0.13113456 and CMake 3.31.6. Set `ANDROID_HOME` and accept SDK licenses. Initial dependency downloads require networking; running the built app does not.

```sh
git clone https://github.com/Pascaline06/offline-atlas.git
cd offline-atlas
git checkout codex/offline-atlas-completion
cat third_party/llama-cpp-vendor.tar.gz.part* | tar -xzf - -C third_party
python3 tools/check.py
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
```

The Gradle 8.10.2 wrapper is checked in and its distribution SHA-256 pinned. The native archives preserve llama.cpp at `2145525a4081d66ff1a87cf43ef809f95a85ac0c`. Android build/signing configuration is in [.github/workflows/android-build.yml](.github/workflows/android-build.yml). A local debug build uses your own debug key; installing it over the public signed preview requires uninstalling that preview, which removes its private assets. No private signing key is needed to reproduce functionality.

Rebuild the exact corpus using the release's `source-inputs.json`:

```sh
python3 -m pip install pyarrow==18.1.0
python3 tools/build_pack.py --output build/knowledge --manifest /path/to/source-inputs.json
```

The tool verifies input hashes and records article caps and resulting pack hashes. Without a manifest it tries Wikimedia dumps and then a versioned mirror. Different sources produce different packs. `tools/retrieve_passages.py` probes the shared Java query plan/ranker; retrieval coverage is not answer quality. Historical `query_index.py` and `probe_research.py` preserve the old audit and do not represent the current Android research path.

## Validation and submission

Follow [the real-device protocol](docs/DEVICE-VALIDATION.md), [whole-answer evaluation](docs/WHOLE-ANSWER-EVAL.md) and [the requirement matrix and two-day plan](docs/BOUNTY-STATUS.md). The [initial audit](docs/INITIAL-AUDIT.md) explains the previous failures; [v0.1 history](docs/HISTORY-0.1.md) is retained separately. Code is in [PR #2](https://github.com/Pascaline06/offline-atlas/pull/2).

The bounty requires a real offline phone demo, independently assessable research quality and public X/Farcaster + poidh proof. Those are not established by compilation or an APK download. A win cannot be guaranteed by this project.

Original project code is MIT licensed; upstream code, weights and datasets retain their own licenses. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
