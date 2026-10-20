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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
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
}