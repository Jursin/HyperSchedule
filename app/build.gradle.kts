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

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.hyperschedule.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hyperschedule.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"

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

    testOptions {
        unitTests {
            // Robolectric 要读通知渠道、字符串等资源
            isIncludeAndroidResources = true
            // android-all 由 Robolectric 运行时自行下载，直连 Maven Central 常被中断，走镜像
            all {
                it.systemProperty("robolectric.dependency.repo.id", "aliyun-public")
                it.systemProperty("robolectric.dependency.repo.url", "https://maven.aliyun.com/repository/public")
                // Robolectric 要加载 native runtime，JDK 24 起不加这个会打印受限方法警告
                it.jvmArgs("--enable-native-access=ALL-UNNAMED")
            }
        }
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
    ksp(libs.androidx.room.compiler)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.nav)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.core)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
