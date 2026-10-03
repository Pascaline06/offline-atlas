# Bounty requirement status and remaining two-day plan

Updated for Offline Atlas 0.2, 2026-10-03. “Implemented” means reviewed code/build behavior. Device and independent quality claims require the separate evidence listed here.

| Requirement | Evidence / status | Remaining gate |
| --- | --- | --- |
| Android + compatible GrapheneOS hardware | arm64 APK, Android 11+; no Play Services dependency | exact preview tested on real supported Pixel/GrapheneOS |
| Maximum 12 GB RAM | supported 2.50 GB Q4_K_M model; bounded 4096-token context; on-device PSS/RAM logs | cold/warm/sustained peak and whole-device memory on <=12 GB phone |
| Maximum 50 GB total assets | real database 4,540,456,960 bytes + model 2,497,281,120 bytes; bounded atomic imports | complete installation/download/cache/log accounting on test phone |
| Fully offline after install | manifest has no Internet permission; in-process retrieval/inference; source URLs not opened | disconnected real-device launch/reboot/demo |
| No API calls, remote inference, web search or runtime requests | none in core app; CI/desktop preparation tools download assets separately | APK permission inspection plus real-device observation |
| No Google Play Services | native Android widgets/SQLite, Kotlin coroutines and JNI | GrapheneOS test without Play installation |
| Explanation, comparison, synthesis, reasoning | passage retrieval, four-source generation, two-sided comparison, stable knowledge recovery | independent whole-answer grading on unfamiliar questions |
| Usable phone speed | streaming, cancellation, shared timeout, timing export | measured p50/p95, sustained latency/thermal behavior |
| Public GitHub | public completion branch, PR #2, public preparation pack and signed preview workflow | exact final functional version/tag retained when claiming |
| Reproducible code/assets/dependencies/instructions | wrapper checksum, pinned native sources/model, source URLs/hashes, APK/pack release assets | fresh installation by another person without debugging |
| Models/datasets/index resources documented | model manifest, source-inputs.json, README/NOTICE, SQLite schema 5 | include exact evidence report with final release |
| Real phone works in a few minutes | simple APK + two asset imports | independent fresh-install timing; downloads may depend on connection speed |
| >50% useful versus internet + frontier models | blinded paired scoring and full-run checks implemented | collect baseline and independent >=100-question phone grades |
| Public X/Farcaster demo + poidh screenshot/link | proof protocol prepared | record actual phone demo; publish genuine post and claim |

## Day 1: finish device viability and obvious failures

0–2 hours: install exact preview + assets on a <=12 GB compatible Pixel. Run USB smoke tests and a genuine disconnected explanation/comparison/synthesis/reasoning demo. Test stop/retry, rotations, restart/reboot and bad imports. Preserve raw observations.

2–6 hours: run an independently frozen 100-question batch. Collect cold/warm memory, full asset accounting and first-draft/final latency. Fix reproduced crashes, corrupt-state recovery or performance failures; rerun affected checks. If warm latency misses targets, reduce context/passages or completion length and re-evaluate quality rather than announcing success.

6–10 hours: independently inspect answer failures. Expand the documented corpus for actual coverage gaps or improve query/ranking behavior. No special cases for demo questions. Freeze the candidate version before final evaluation.

## Day 2: prove quality, reproduce, and prepare a legitimate claim

0–5 hours: collect internet-enabled frontier baseline answers for the independently frozen set. Have independent graders score both sides blind. Publish complete answers, grading rubric, scores, uncertainty and failures. If quality remains below the bar, do not label the submission compliant; use another fresh set after material fixes.

5–8 hours: have another person perform a fresh installation from public release instructions, run the exact candidate offline on GrapheneOS, and verify RAM/storage/latency. Finalize a versioned functional repository state and checksums.

8–10 hours: record public demo showing airplane mode and disabled Wi-Fi, several difficult research questions, honest mode/source labels, device specs, final repo/tag link and approach. Post to X or Farcaster and submit the genuine screenshot/links to poidh. The functional reviewed version must already exist publicly when claiming.

Winning depends on the bounty judges or a qualifying public confirmation from Vitalik. No compilation, test suite, parameter count or self-evaluation can guarantee it.
