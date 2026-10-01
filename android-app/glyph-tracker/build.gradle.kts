plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uk.nothingsuite.glyphtracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.nothingsuite.glyphtracker"
        minSdk = 31            // Nothing phones only; Phone (1) shipped on Android 12
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Glyph Matrix SDK 2.0 — covers the strip phones (GlyphManager) and the
    // Phone (3) matrix (GlyphMatrixManager). Under Nothing's EULA, so git-ignored:
    // see android-app/glyph-sdk/GLYPH-SDK-LICENCE.md.
    val glyphSdk = rootProject.file("glyph-sdk/glyph-matrix-sdk-2.0.aar")
    if (!glyphSdk.exists()) throw GradleException("Missing ${glyphSdk.path} — see android-app/glyph-sdk/GLYPH-SDK-LICENCE.md")
    implementation(files(glyphSdk))
}
