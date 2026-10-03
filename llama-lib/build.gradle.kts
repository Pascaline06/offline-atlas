plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.arm.aichat"
    compileSdk = 35
    ndkVersion = "29.0.13113456"
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake {
            arguments += listOf("-DBUILD_SHARED_LIBS=ON", "-DLLAMA_BUILD_APP=OFF", "-DLLAMA_BUILD_COMMON=ON",
                "-DLLAMA_OPENSSL=OFF", "-DGGML_NATIVE=OFF", "-DGGML_BACKEND_DL=ON",
                "-DGGML_CPU_ALL_VARIANTS=ON", "-DGGML_LLAMAFILE=OFF",
                // Debug APK still needs optimized inference and compact native libraries.
                "-DCMAKE_C_FLAGS_DEBUG=-O3 -DNDEBUG -g0",
                "-DCMAKE_CXX_FLAGS_DEBUG=-O3 -DNDEBUG -g0")
        } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.31.6" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2"); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0") }
