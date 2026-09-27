# Local model

The app vendors llama.cpp (commit `2145525a4081d66ff1a87cf43ef809f95a85ac0c`, MIT license) and its official Android JNI example as an arm64 library. It runs GGUF inference in the app process without a network permission or Play Services. The model is not bundled with the APK. The recommended non-thinking Qwen3-4B-Instruct-2507 Q4_K_M GGUF is described with URL and verified checksum in `data/model-manifest.json` (2,497,281,120 bytes).

Download the file on Wi-Fi, check its checksum, then use the app's **Select local GGUF model** button. The app copies it to private storage, loads it, and generates from up to four locally retrieved evidence passages. It still shows those passages for verification. Source URLs refer to the original source, but are never opened by the app. The system prompt asks the model to cite `[1]` etc. Such prompting does not establish factual correctness; benchmark citations against source text and report hallucinations.

The model may use several GB of RAM in addition to its quantized weights, retrieval index and Android. No performance or peak memory claim has been verified on a phone yet.
