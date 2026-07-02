import com.android.build.gradle.tasks.MergeSourceSetFolders
import org.gradle.kotlin.dsl.coreLibraryDesugaring
import java.util.Properties
import java.util.Random
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "2.2.0"
    id("com.google.devtools.ksp") version "2.3.9"
    id("com.google.dagger.hilt.android")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("release-keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val generatedAssetsDir = layout.buildDirectory.dir("generated_assets")
val generateOpenMWAssets = tasks.register("generateOpenMWAssets") {
    outputs.dir(generatedAssetsDir)
    doLast {
        generatedAssetsDir.get().asFile.mkdirs()
    }
}

// Task to restore pinned native libraries after CMake overwrites them.
// These libraries must match the versions used in the working backup build
// (openmw-ds-20260702.apk) — fresh CMake builds produce incompatible versions.
val restorePinnedLibs = tasks.register("restorePinnedLibs") {
    val backupLibsDir = file("src/main/backup-libs/arm64-v8a")
    val jniLibsDir = file("src/main/jniLibs/arm64-v8a")
    inputs.dir(backupLibsDir)
    outputs.dir(jniLibsDir)
    doLast {
        backupLibsDir.listFiles()?.forEach { lib ->
            lib.copyTo(File(jniLibsDir, lib.name), overwrite = true)
            println("Restored pinned lib: ${lib.name}")
        }
    }
}

android {
    namespace = "org.openmw"
    compileSdk = 37
    ndkVersion = "29.0.14206865"
    buildToolsVersion = "36.0.0"

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
            storeFile = keystoreProperties.getProperty("storeFile")?.let { file(it) }
            storePassword = keystoreProperties.getProperty("storePassword")
        }
    }

    defaultConfig {
        applicationId = "com.alpha3.launcher"
        minSdk = 26

        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 2
        versionName = providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()

        buildConfigField("int", "RANDOMIZER", "${Random().nextInt(999).let { if (it < 0) -it else it }}")

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a")
        }

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                val generatedAssetsPath =
                    generatedAssetsDir.get().asFile.invariantSeparatorsPath
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DGENERATED_ASSETS_DIR=$generatedAssetsPath",
                    "-DOPENMW_SRC=${project.findProperty("OPENMW_SRC") ?: ""}"
                )
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        multiDexEnabled = true
    }

    androidComponents.onVariants { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        tasks.withType<MergeSourceSetFolders>().configureEach {
            if (name == "merge${cap}Assets") {
                dependsOn(generateOpenMWAssets)
                dependsOn("buildCMake$cap[arm64-v8a]")
            }
        }
        tasks.matching { it.name == "buildCMake${cap}[arm64-v8a]" }.configureEach {
            finalizedBy(restorePinnedLibs)
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
            // Keep the macOS (Android Studio) and Linux (Docker) native caches
            // apart so an IDE sync can never poison the container build.
            if (System.getenv("ALPHA3_DOCKER") != null) {
                buildStagingDirectory = file(".cxx-docker")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
            /*isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                          "proguard-rules.pro"
            )
            isJniDebuggable = false*/
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "2.2.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            //keepDebugSymbols += "**/*.so"
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.directories.add("src/main/jniLibs")
            assets.directories.add(generatedAssetsDir.get().asFile.absolutePath)
        }
    }
    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
    }
}

dependencies {
    // androidx/ktx
    implementation(libs.core.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.runner)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)
    coreLibraryDesugaring(libs.desugar.jdk.libs) // support some java8 features

    // compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)
    implementation(libs.reorderable)
    implementation(libs.colorpicker.compose)

    // compose adaptive
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation)

    // Hilt injector
    ksp(libs.kotlin.metadata.jvm)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // data store
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore)

    // web/parse
    implementation(libs.androidx.webkit)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.gson)
    implementation(libs.protobuf.javalite)
    implementation(libs.guava)

    // lazy to organize XD
    implementation(libs.relinker)
    implementation(libs.androidx.window)
    implementation(libs.animate.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.core)
    implementation(libs.org.eclipse.jgit)
    implementation(libs.translate)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.accompanist.pager)
    implementation(libs.accompanist.pager.indicators)
    implementation(libs.x.zip.jbinding.xandroid)
    implementation(libs.bcprov.jdk18on)

    // rooms
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
