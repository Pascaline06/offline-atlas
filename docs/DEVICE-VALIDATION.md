# Real-device validation

Use the exact public APK, knowledge-pack manifest and supported model on a real compatible Pixel running GrapheneOS, with at most 12 GB RAM. Also test stock Android if available. A desktop CPU or emulator run does not satisfy this gate. Save release URLs, APK hash, model hash, pack hash, OS build, model name and physical RAM with each report.

## Smoke test over USB

Install Android platform-tools on the host and enable USB debugging on the phone. Download the preview and its matching tests APK from the same release.

```sh
bash tools/device_smoke.sh offline-atlas-0.2.0.apk offline-atlas-0.2.0-tests.apk evidence/smoke
```

This launches the real app, checks empty-query behavior, fixture suppression and absence of the Internet permission. It does not test generation. Instrumentation tests are built in CI but require a physical device to execute.

## Offline and lifecycle test

Install the real pack and model through the app. Enable airplane mode, explicitly disable Wi-Fi, disconnect USB tethering/Ethernet/VPN and record the settings. Start a screen recording or film the phone externally. Run an explanation, comparison, synthesis and reasoning question. Show full sources, dates and the knowledge-mode label when used. Test a live-hours question and a missing topic; demonstrate the limitations rather than hiding them.

While an answer is generating, tap Stop; immediately ask another question. Repeat during prompt processing and source verification. Rotate the phone, background/foreground it, close/reopen it and reboot. It must restore private assets without downloads. Check a truncated/wrong model and a broken ZIP: previous usable assets must survive rejected replacements.

## Frozen research run

An independent person should supply at least 100 questions and freeze the file/hash before testing. Include factual recall, explanations, comparisons, synthesis, multi-step reasoning, unfamiliar topics, misleading premises and current-information requests. Existing repository question sets have been used during development; they are not a fresh held-out set.

```sh
python3 tools/device_benchmark.py independent-questions.txt evidence/pixel-run
```

The tool drives the actual Android UI via USB and saves complete private JSONL observations. Keep failures and refusals. Do not edit answers, retry selectively, or substitute desktop answers. Inspect instrumentation output and every recorded question. The signed preview is a debug build so `adb run-as` can read its private test log; normal app use needs no adb.

## Resource and speed evidence

The app samples process PSS every 500 ms while answering and records RAM, private files/cache, APK/native-library storage, pack bytes, actual model context and timing. Sampling can miss a short memory peak. Record cold-load memory separately with `adb shell dumpsys meminfo org.offlineatlas.preview`, and use a system profiler or Perfetto if peak accounting is uncertain. Include the OS's memory use; fitting process PSS within 12 GB alone is insufficient.

Count original downloaded weights/ZIPs if retained, installed APK/native libraries, model, database/indexes, cache and logs against the 50 GB limit. The default DB plus model is 7,037,738,080 bytes before app/cache/log overhead; keeping the published ZIP and original model adds about 3.95 GB. Imports leave room for private overhead and reject oversized replacements. Larger custom packs need their own complete accounting.

Report cold/warm load, retrieval, time to first unverified draft, time to final useful answer, p50/p95 latency, rejected/cancelled answers, crashes, thermal throttling and sustained performance. First streamed draft is not the same as a usable final answer. A 90-second cutoff does not prove speed is acceptable. Set usability thresholds before testing; proposed engineering targets are retrieval p95 < 1 second, warm first draft p95 < 8 seconds and useful final answers p95 < 45 seconds, with no crashes. These are targets, not measured claims or bounty rules.

Save raw logs, full answers, hardware photos and offline demonstration video. Record a final public demo only after this exact release passes the device gates.
