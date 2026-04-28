import java.net.URI
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application")
}

abstract class GenerateNetworkSecurityConfigTask : DefaultTask() {
    @get:Input
    abstract val host: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val xmlDir = outputDir.get().dir("xml").asFile
        xmlDir.mkdirs()
        xmlDir.resolve("network_security_config.xml").writeText(
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<network-security-config>
            |    <domain-config cleartextTrafficPermitted="true">
            |        <domain includeSubdomains="false">${host.get()}</domain>
            |    </domain-config>
            |</network-security-config>
            |""".trimMargin()
        )
    }
}

val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Hardcoded so F-Droid (which can't read gitignored files or repo secrets) builds reproducibly.
val productionApiBaseUrl = "https://werewolf.camp/brogue-api"

fun envValue(envFile: String, key: String): String {
    val f = rootProject.file(envFile)
    if (!f.exists()) {
        throw GradleException(
            "Missing $envFile. Copy $envFile.template to $envFile and fill in real values."
        )
    }
    val line = f.readLines()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key=") }
        ?: throw GradleException("Missing key '$key' in $envFile")
    val value = line.substringAfter("=").trim()
    if (value.isBlank()) {
        throw GradleException("Empty value for '$key' in $envFile")
    }
    return value
}

// Configure the staging variant only when .env exists OR a staging task is invoked.
// Lets `assembleRelease` and unrelated gradle commands run without .env present, while
// `assembleStaging` fails with a clear "Missing .env..." error from envValue().
val stagingRequested = gradle.startParameter.taskNames.any { it.contains("staging", ignoreCase = true) }
val configureStaging = rootProject.file(".env").exists() || stagingRequested

android {
    namespace = "org.broguece.game"
    compileSdk = 35
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "org.broguece.game"
        minSdk = 24
        targetSdk = 35
        versionCode = 1150117
        versionName = "1.15.1.17"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                )
                cFlags += listOf(
                    "-std=c99",
                    "-Wall",
                    "-Wno-parentheses",
                    "-Wno-unused-result",
                    "-Wno-format",
                    "-Wno-incompatible-pointer-types-discards-qualifiers",
                    "-O2",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        getByName("debug") // uses ~/.android/debug.keystore automatically
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        if (configureStaging) {
            create("staging") {
                initWith(getByName("debug"))
                isDebuggable = true
                isJniDebuggable = true
                signingConfig = signingConfigs.getByName("debug")
                buildConfigField(
                    "String", "API_BASE_URL",
                    "\"${envValue(".env", "API_STAGING_URL")}\""
                )
                // Library modules don't define `staging` — fall back to `debug`.
                matchingFallbacks += listOf("debug")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            buildConfigField("String", "API_BASE_URL", "\"$productionApiBaseUrl\"")
        }
    }

    sourceSets {
        getByName("main") {
            // Game assets (tiles, icons, keymap)
            assets.srcDirs("../../bin/assets")
            // SDL2's required Java glue classes (SDLActivity, etc.)
            java.srcDirs(
                "src/main/java",
                "../SDL2/android-project/app/src/main/java"
            )
        }
    }
}

// Disable AGP's auto-created `debug` variant — force staging or release.
androidComponents {
    beforeVariants(selector().withBuildType("debug")) { variant ->
        variant.enable = false
    }
}

// Staging's network-security config is generated so the host never lives in tracked source.
androidComponents {
    onVariants(selector().withBuildType("staging")) { variant ->
        val envUrl = envValue(".env", "API_STAGING_URL")
        val hostValue = URI(envUrl).host
            ?: throw GradleException("API_STAGING_URL in .env has no host component: $envUrl")

        val genTask = tasks.register<GenerateNetworkSecurityConfigTask>(
            "generate${variant.name.replaceFirstChar { it.uppercase() }}NetworkSecurityConfig"
        ) {
            host.set(hostValue)
            outputDir.set(layout.buildDirectory.dir("generated/netsec/${variant.name}"))
        }
        variant.sources.res?.addGeneratedSourceDirectory(
            genTask,
            GenerateNetworkSecurityConfigTask::outputDir
        )
    }
}

dependencies {
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
