import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Neutral signing configuration supports environment variables or user Gradle properties.
// A complete neutral profile takes precedence; the compatible profile is selected as a whole.
val signingFields = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val signingEnv = listOf("MY_AUDIO_STORE_FILE", "MY_AUDIO_STORE_PASSWORD", "MY_AUDIO_KEY_ALIAS", "MY_AUDIO_KEY_PASSWORD")
val neutralSigning = signingFields.mapIndexed { index, field ->
    providers.environmentVariable(signingEnv[index]).orNull?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("myAudio.signing.$field").orNull?.takeIf { it.isNotBlank() }
}
val compatibleSigning = listOf("StoreFile", "StorePassword", "KeyAlias", "KeyPassword").map { field ->
    providers.gradleProperty("martensening$field").orNull?.takeIf { it.isNotBlank() }
}
val releaseSigning = if (neutralSigning.any { it != null }) neutralSigning else compatibleSigning

android {
    namespace = "com.martensening.my_audio"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.martensening.my_audio"
        minSdk = 29
        targetSdk = 36
        // Shared with the desktop app, see gradle.properties.
        versionCode = providers.gradleProperty("myAudio.versionCode").get().toInt()
        versionName = providers.gradleProperty("myAudio.versionName").get()

        ndk {
            // Pixel 9 and the x86_64 emulator.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                // Oboe's prefab package requires the shared C++ runtime.
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    // Debug uses Android's local debug key. An incomplete release profile stays unsigned.
    signingConfigs {
        if (releaseSigning.all { it != null }) {
            create("release") {
                storeFile = file(releaseSigning[0]!!)
                storePassword = releaseSigning[1]
                keyAlias = releaseSigning[2]
                keyPassword = releaseSigning[3]
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Texts and icons live in :shared and are mostly used through Compose Multiplatform's Res,
        // which Android lint cannot see; every shared resource would be reported as unused.
        disable += "UnusedResources"
    }
    buildFeatures {
        compose = true
        prefab = true
    }
    sourceSets {
        getByName("main") {
            // Texts and icons live once in :shared (Compose Multiplatform resources use the Android
            // resource layout), so Android code keeps R.string/R.drawable and shared code uses Res.
            res.directories.add("../shared/src/commonMain/composeResources")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.oboe)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    // Android stubs org.json in JVM tests; the real implementation backs ProjectRepositoryTest.
    testImplementation(libs.org.json)
}

// Compare the APK certificate with the developer-selected keystore, never with a fixed publisher.
// Only the public certificate fingerprint is written; password values are neither logged nor saved.
tasks.register("writeReleaseSigningFingerprint") {
    doLast {
        check(releaseSigning.all { it != null }) {
            "Release signing is not configured. Use build.ps1 -Target Android for a debug APK, or configure myAudio.signing.* locally."
        }
        val keyStore = KeyStore.getInstance(file(releaseSigning[0]!!), releaseSigning[1]!!.toCharArray())
        val certificate = checkNotNull(keyStore.getCertificate(releaseSigning[2]!!)) { "Signing alias has no certificate." }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
        val output = layout.buildDirectory.file("signing/release-certificate.sha256").get().asFile
        output.parentFile.mkdirs()
        output.writeText(fingerprint + "\n")
    }
}
