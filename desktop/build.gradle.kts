import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// Same version as the Android app (gradle.properties); installers need MAJOR.MINOR.BUILD.
val appVersion = providers.gradleProperty("myAudio.versionName").get()

kotlin {
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.desktop.currentOs)
            implementation(libs.cmp.material3)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.cmp.components.resources)
        }
    }
}

// Native engine (myaudio.dll): MSVC build of desktop/native, see tools/build_desktop_native.ps1.
val nativeDir = layout.buildDirectory.dir("native")
val buildNative by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the native audio engine with MSVC, CMake and Ninja."
    workingDir = rootDir
    commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "tools/build_desktop_native.ps1")
    inputs.dir("native")
    inputs.dir(rootProject.file("app/src/main/cpp"))
    inputs.file(rootProject.file("tools/build_desktop_native.ps1"))
    inputs.file(rootProject.file("tools/dev_environment.ps1"))
    inputs.file(rootProject.file("app/build.gradle.kts"))
    outputs.dir(nativeDir)
}

// The packaged app carries myaudio.dll as an app resource (compose.application.resources.dir).
val appResources = layout.buildDirectory.dir("appResources")
val stageNativeResources by tasks.registering(Copy::class) {
    dependsOn(buildNative)
    from(nativeDir) { include("*.dll", "*.so") }
    into(appResources.map { it.dir("windows") })
}

/** jpackage is missing from Android Studio's JBR; installers are built with Temurin 21 (see repository-rules). */
val packagingJdk: String? = providers.gradleProperty("myAudio.packagingJdk").orNull
    ?: System.getenv("MY_AUDIO_JDK")?.takeIf { File(it, "bin/jpackage.exe").isFile }
    ?: System.getenv("JAVA_HOME")?.takeIf { File(it, "bin/jpackage.exe").isFile }
    ?: File(System.getenv("ProgramFiles") ?: "C:/Program Files", "Eclipse Adoptium")
        .listFiles { file -> file.isDirectory && file.name.startsWith("jdk-21") }
        ?.maxByOrNull { it.name }?.absolutePath

compose.desktop {
    application {
        mainClass = "com.martensening.my_audio.desktop.MainKt"
        jvmArgs += "-Dmyaudio.version=$appVersion"
        packagingJdk?.let { javaHome = it }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            appResourcesRootDir.set(appResources)
            // Modules found with suggestRuntimeModules; jdk.localedata for German numbers and dates.
            modules("java.instrument", "java.management", "java.naming", "jdk.unsupported", "jdk.localedata")
            packageName = "My Audio"
            // MSI needs MAJOR.MINOR.BUILD: "0.8" becomes 0.8.0, "0.8.1" stays.
            packageVersion = if (appVersion.count { it == '.' } >= 2) appVersion else "$appVersion.0"
            vendor = "Björn Martensen"
            description = "Multitrack recorder for musical ideas and demos"
            windows {
                menuGroup = "My Audio"
                // Installs for the current user, without administrator rights.
                perUserInstall = true
                dirChooser = true
                shortcut = true
                menu = true
                // Rounded icon from tools/desktop_icon.py.
                iconFile.set(project.file("icons/my-audio.ico"))
                // Stable upgrade code so newer MSIs replace older installs.
                upgradeUuid = "6f1d2a43-5b8e-4c47-9a5e-3f0c8b7e2d11"
            }
            linux {
                packageName = "my-audio"
                iconFile.set(project.file("src/jvmMain/resources/icon.png"))
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(stageNativeResources) }
// Development runs load the library straight from the CMake output; installers use the app resources.
tasks.matching { it.name == "run" }.configureEach {
    dependsOn(buildNative)
    (this as JavaExec).systemProperty("myaudio.native.dir", nativeDir.get().asFile.absolutePath)
    providers.gradleProperty("myAudio.openProject").orNull?.let { systemProperty("myaudio.openProject", it) }
}
