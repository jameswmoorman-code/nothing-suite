plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uk.nothingsuite.dotwidgets"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.nothingsuite.dotwidgets"
        minSdk = 26            // Any Android 8+ phone — this one is NOT Nothing-only
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core-design"))
    implementation(project(":core-billing"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Glyph Matrix SDK 2.0 — under Nothing's EULA, so the .aar is git-ignored.
    // Get it from github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit and drop it in android-app/glyph-sdk/.
    val glyphSdk = rootProject.file("glyph-sdk/glyph-matrix-sdk-2.0.aar")
    if (!glyphSdk.exists()) throw GradleException("Missing ${glyphSdk.path} — see android-app/glyph-sdk/GLYPH-SDK-LICENCE.md")
    implementation(files(glyphSdk))
}
