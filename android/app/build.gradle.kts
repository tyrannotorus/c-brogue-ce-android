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

// Reads a single KEY=value from a gitignored .env file at the Android
// project root. Fails the build if the file or the key is missing — the
// templates (.env.staging.template, .env.production.template) advertise what's
// expected; a missing value must never silently fall back to empty so the
// staging URL can't slip into a release build.
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
    return line.substringAfter("=").trim()
}

android {
    namespace = "org.broguece.game"
    compileSdk = 35
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "org.broguece.game"
        minSdk = 24
        targetSdk = 35
        versionCode = 1150115
        versionName = "1.15.1.15"

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

    // Two and only two variants:
    //   assembleStaging → debuggable, reads .env.staging
    //   assembleRelease → production,  reads .env.production
    // The default `debug` variant is filtered out below so it can't be built
    // or shipped without an .env injected.
    buildTypes {
        create("staging") {
            initWith(getByName("debug"))
            isDebuggable = true
            isJniDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            // Read .env.staging when present; fall back to an empty URL
            // when missing. The variant is disabled further down in that
            // case (see `beforeVariants`), so this value never lands in a
            // shipped APK — it exists only because AGP evaluates the
            // buildType block eagerly during configuration.
            val stagingUrl = if (rootProject.file(".env.staging").exists())
                envValue(".env.staging", "API_BASE_URL")
            else ""
            buildConfigField("String", "API_BASE_URL", "\"$stagingUrl\"")
            // Library modules don't define our `staging` type — fall back to
            // their `debug` when Gradle resolves dependency variants.
            matchingFallbacks += listOf("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
            buildConfigField(
                "String", "API_BASE_URL",
                "\"${envValue(".env.production", "API_BASE_URL")}\""
            )
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

// Hide the default `debug` variant — AGP always creates it, but we don't want
// it bought into: a plain `assembleDebug` would ship without any .env injected
// at all. Forcing the choice between Staging and Release keeps the IP-leak
// guard rail honest.
//
// Also disable the `staging` variant when `.env.staging` is absent (the
// GitHub release CI only ships .env.production). This avoids forcing CI
// to fabricate a placeholder `.env.staging` just to satisfy configuration-
// phase validation.
val stagingEnvFile = rootProject.file(".env.staging")
androidComponents {
    beforeVariants(selector().withBuildType("debug")) { variant ->
        variant.enable = false
    }
    if (!stagingEnvFile.exists()) {
        beforeVariants(selector().withBuildType("staging")) { variant ->
            variant.enable = false
        }
    }
}

// Staging's network-security config is generated from .env.staging so the
// host never lives in tracked source. Release uses src/release/res/xml.
androidComponents {
    onVariants(selector().withBuildType("staging")) { variant ->
        // Skip when the staging variant is disabled (no .env.staging present).
        if (!stagingEnvFile.exists()) return@onVariants

        val envUrl = envValue(".env.staging", "API_BASE_URL")
        val hostValue = URI(envUrl).host
            ?: throw GradleException("API_BASE_URL in .env.staging has no host component: $envUrl")

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
    // Pull-to-refresh on the Play Seeds modal. AndroidX is already enabled in
    // gradle.properties; this is Google's own library, ~200KB on wire.
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
