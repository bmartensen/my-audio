plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.martensening.my_audio.shared"
        compileSdk = 37
        minSdk = 29
        // Packs the Compose resources for shared screens on Android.
        androidResources { enable = true }
    }
    jvm("desktop") { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.material3)
            implementation(libs.kotlinx.serialization.json)
            api(libs.cmp.components.resources)
        }
        // JVM code shared by Android and desktop (files, ZIP archives, project storage).
        val jvmSharedMain by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmSharedMain)
        named("desktopMain") { dependsOn(jvmSharedMain) }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
        named("desktopTest").dependencies {
            implementation(libs.junit)
            // Tests edit project files with org.json, which also checks the files stay readable for it.
            implementation(libs.org.json)
        }
    }
}


// Bundle original-project and third-party notices verbatim in the shared About view.
// Bounded chunks avoid JVM string/method limits and allow lazy rendering on small devices.
// The project license and the credits inventory lead; full upstream texts follow by path.
val leadingLicenseFiles = listOf(rootProject.file("LICENSE"), rootProject.file("licenses/THIRD_PARTY_NOTICES.md"))
val licenseTexts = rootProject.fileTree("licenses") { include("**/*.txt") }
val generateLicenseText by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/license/kotlin")
    inputs.files(leadingLicenseFiles, licenseTexts)
    outputs.dir(outDir)
    doLast {
        val sections = (leadingLicenseFiles + licenseTexts.files.sortedBy { it.relativeTo(rootDir).invariantSeparatorsPath })
            .flatMap { file ->
                val heading = file.relativeTo(rootDir).invariantSeparatorsPath
                ("$heading\n${"=".repeat(72)}\n${file.readText().replace("\r\n", "\n")}\n").chunked(6000)
            }
        val literals = sections.joinToString(",\n") { section ->
            "    \"" + section.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("$", "\\$").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
        }
        val target = outDir.get().file("com/martensening/my_audio/ui/LicenseText.kt").asFile
        target.parentFile.mkdirs()
        target.writeText(
            "package com.martensening.my_audio.ui\n\n" +
                "/** Generated from LICENSE and the licenses directory, including THIRD_PARTY_NOTICES.md. */\n" +
                "internal val LicenseSections: List<String> = listOf(\n$literals\n)\n"
        )
    }
}
kotlin.sourceSets.commonMain { kotlin.srcDir(generateLicenseText) }

compose.resources {
    packageOfResClass = "com.martensening.my_audio.resources"
    publicResClass = true
}
