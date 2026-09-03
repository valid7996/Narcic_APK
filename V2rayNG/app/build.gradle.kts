plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.jaredsburrows.license")
}

android {
    namespace = "com.narcic.ng"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.narcic.ng"
        minSdk = 24
        targetSdk = 37
        // Overridable from CI so the released APK's version always matches
        // the pushed git tag, e.g. -PNARCIC_VERSION_NAME=2.1.6 -PNARCIC_VERSION_CODE=1206
        versionCode = (properties["NARCIC_VERSION_CODE"] as? String)?.toIntOrNull() ?: 742
        versionName = (properties["NARCIC_VERSION_NAME"] as? String) ?: "2.3.2"

        val abiFilterList = (properties["ABI_FILTERS"] as? String)?.split(';')
        splits {
            abi {
                isEnable = true
                reset()
                if (!abiFilterList.isNullOrEmpty()) {
                    include(*abiFilterList.toTypedArray())
                } else {
                    include(
                        "arm64-v8a",
                        "armeabi-v7a",
                        "x86_64",
                        "x86"
                    )
                }
                isUniversalApk = abiFilterList.isNullOrEmpty()
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                abiFilters += if (!abiFilterList.isNullOrEmpty()) {
                    abiFilterList
                } else {
                    listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions.add("distribution")
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroid"
            buildConfigField("String", "DISTRIBUTION", "\"F-Droid\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"Play Store\"")
        }
    }

    sourceSets {
        getByName("main") {
            // "libs" holds the prebuilt libv2ray AAR + the psiphontunnel AAR
            // (W on N); src/main/jniLibs holds libtor.so / libobfs4proxy.so
            // (Tor path) and libaether.so when the Rust core has been built.
            jniLibs.srcDirs("libs", "src/main/jniLibs")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    applicationVariants.all {
        val variant = this
        val isFdroid = variant.productFlavors.any { it.name == "fdroid" }
        if (isFdroid) {
            val versionCodes =
                mapOf(
                    "armeabi-v7a" to 2, "arm64-v8a" to 1, "x86" to 4, "x86_64" to 3, "universal" to 0
                )

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = output.getFilter("ABI") ?: "universal"
                    output.outputFileName = "v2rayNG_${variant.versionName}-fdroid_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (100 * variant.versionCode + versionCodes[abi]!!).plus(5000000)
                    } else {
                        return@forEach
                    }
                }
        } else {
            val versionCodes =
                mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = if (output.getFilter("ABI") != null)
                        output.getFilter("ABI")
                    else
                        "universal"

                    output.outputFileName = "v2rayNG_${variant.versionName}_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (1000000 * versionCodes[abi]!!).plus(variant.versionCode)
                    } else {
                        return@forEach
                    }
                }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            // Both the libv2ray AAR (Xray core) and the psiphontunnel AAR ship
            // their Go runtime as libgojni.so. There can be only one Go runtime
            // per process and the two never clash at runtime (Psiphon and Xray
            // coexist through gomobile's single-runtime constraint) — the
            // duplicate is only the filename. Keep the first one encountered.
            pickFirsts += "lib/arm64-v8a/libgojni.so"
            pickFirsts += "lib/armeabi-v7a/libgojni.so"
            pickFirsts += "lib/x86/libgojni.so"
            pickFirsts += "lib/x86_64/libgojni.so"
        }
    }

}

dependencies {
dependencies {
    // Core Libraries. The psiphontunnel AAR in libs/ has been patched locally:
    // its gomobile bind runtime classes (go.*) were removed from classes.jar
    // because libv2ray.aar ships the identical ones and Gradle fails on
    // duplicate classes otherwise (checkPlaystoreReleaseDuplicateClasses).
    // The patched AAR keeps everything else (ca.psiphon.*, jni libs) intact.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // AmneziaWG native tunnel engine
    implementation(project(":tunnel"))

    // AndroidX Core Libraries
    implementation(libs.androidx.core.ktx)

    // Compose Libraries
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.lifecycle.runtime.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Data and Storage Libraries
    implementation(libs.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Reactive and Utility Libraries
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // QR Code: CameraX + ZXing
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)
    implementation(libs.core) // zxing core

    // AndroidX Lifecycle and Architecture Components
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Background Task Libraries
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.multiprocess)

    // Reorderable list
    implementation(libs.reorderable)

    // Testing Libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.org.mockito.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

// ---------------------------------------------------------------------------
// W on N (ported from MSN-GUARD): cross-compiles the Rust core (MASQUE /
// WireGuard / WARP-on-WARP transports) into src/main/jniLibs/<abi>/libaether.so.
//
// Requires a Rust toolchain with the Android targets plus cargo-ndk (see
// core/build-android.sh). It is opt-in via -PnarcicBuildAether=true so an
// ordinary Gradle build (which already needs no Rust) never fails on a missing
// toolchain; CI passes the property on release builds. Alternatively drop a
// prebuilt libaether.so into src/main/jniLibs/<abi>/ and skip this entirely —
// the Psiphon and Tor paths do not use the Rust core at all.
val aetherCargoToml = rootProject.file("core/aether/Cargo.toml")
if (aetherCargoToml.exists() &&
    (project.findProperty("narcicBuildAether") as String?)?.toBoolean() == true
) {
    val wonAbis = (project.findProperty("targetAbi") as String?)
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64")

    wonAbis.forEach { abi ->
        val taskName = "buildAether" + abi.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }
        tasks.register<Exec>(taskName) {
            group = "build"
            description = "Builds the W on N Rust core for Android $abi"
            val buildScript = if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
                rootProject.file("core/build-android.ps1")
            } else {
                rootProject.file("core/build-android.sh")
            }
            if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
                commandLine("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", buildScript.absolutePath, "-Abi", abi)
            } else {
                commandLine("bash", buildScript.absolutePath, "--abi", abi)
            }
            environment("ANDROID_HOME", android.sdkDirectory.absolutePath)
            inputs.dir(rootProject.file("core/aether/src"))
            inputs.file(rootProject.file("core/aether/Cargo.toml"))
            inputs.file(rootProject.file("core/aether/Cargo.lock"))
            inputs.dir(rootProject.file("core/quiche"))
            inputs.file(buildScript)
            val output = file("src/main/jniLibs/$abi/libaether.so")
            outputs.file(output)
        }
        tasks.named("preBuild").configure { dependsOn(taskName) }
    }
}
