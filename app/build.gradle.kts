plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "org.offlineatlas"
    compileSdk = 35
    val previewKey = providers.environmentVariable("ATLAS_PREVIEW_KEYSTORE").orNull
    if (previewKey != null) {
        val password = providers.environmentVariable("ATLAS_PREVIEW_KEY_PASSWORD").orNull
            ?: throw GradleException("ATLAS_PREVIEW_KEY_PASSWORD is required with ATLAS_PREVIEW_KEYSTORE")
        signingConfigs {
            create("previewUpdate") {
                storeFile = file(previewKey)
                storePassword = password
                keyAlias = "androiddebugkey"
                keyPassword = password
            }
        }
    }
    defaultConfig {
        applicationId = "org.offlineatlas.preview"
        minSdk = 30
        targetSdk = 35
        versionCode = 22
        versionName = "0.1.21"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    if (previewKey != null) {
        buildTypes.getByName("debug").signingConfig = signingConfigs.getByName("previewUpdate")
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    // GGML discovers libggml-cpu.so by enumerating nativeLibraryDir at runtime.
    // An APK-internal nativeLibraryDir cannot be enumerated as a filesystem directory.
    packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies {
    implementation(project(":llama-lib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

// Import needs a working starter index on first launch. Generate it from the
// checked-in fixtures during clean builds, including builds from source ZIPs.
val generateBundledIndex by tasks.registering(Exec::class) {
    workingDir = rootProject.projectDir
    commandLine("python3", "tools/build_index.py", "--documents", "data/sample_documents.jsonl",
        "--places", "data/sample_places.jsonl", "--output", "app/src/main/assets/atlas.db")
    inputs.files(rootProject.file("tools/build_index.py"), rootProject.file("data/sample_documents.jsonl"),
        rootProject.file("data/sample_places.jsonl"))
    outputs.file(file("src/main/assets/atlas.db"))
}
tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }
    .configureEach { dependsOn(generateBundledIndex) }
