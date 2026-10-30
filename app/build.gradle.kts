@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

aboutLibraries {
    export {
        outputFile = file("src/main/assets/aboutlibraries.json")
        prettyPrint = true
    }
}

// 构建前自动刷新库信息，避免依赖变了 json 还是旧的
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("exportLibraryDefinitions")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.miuix.hyperschedule"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.miuix.hyperschedule"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

    }

    val releaseStoreFile = localProps.getProperty("signing.storeFile", "")
    if (releaseStoreFile.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = localProps.getProperty("signing.storePassword", "")
                keyAlias = localProps.getProperty("signing.keyAlias", "")
                keyPassword = localProps.getProperty("signing.keyPassword", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseStoreFile.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    // 只保留中文与英文资源，其余语言不参与编译
    androidResources {
        localeFilters += listOf("zh", "en")
    }

    packaging {
        resources {
            excludes +=
                listOf(
                    "META-INF/*.version",
                    "META-INF/androidx/**/LICENSE.txt",
                    "META-INF/org/**/LICENSE.txt",
                    "META-INF/com/android/build/**",
                    "META-INF/com/mikepenz/**",
                    "META-INF/version-control-info.textproto",
                    "DebugProbesKt.bin",
                    "kotlin/**",
                )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}

// 产物按架构命名
androidComponents {
    onVariants { variant ->
        val buildType = variant.buildType ?: variant.name
        variant.outputs.forEach { output ->
            val abi =
                output.filters
                    .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                    ?.identifier
            output.outputFileName.set("hyperschedule-${abi ?: "universal"}-$buildType.apk")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.nav)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.core)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
}
