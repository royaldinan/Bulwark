plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Single source of truth for the Mediation API base URL (PRD task 1).
// Resolved from a Gradle property or env var so the deployed Railway URL is not baked in code.
// VERIFY: the real Railway URL is set by the backend owner (do not invent endpoints).
// .invalid is a reserved TLD used here as an obvious, overridable placeholder.
val apiBaseUrl: String = (project.findProperty("bulwark.apiBaseUrl") as String?)
    ?: System.getenv("BULWARK_API_BASE_URL")
    ?: "https://bulwark-api.example.invalid"

// VERIFY: POST /v1/report requires the shared token (PRD "Keputusan teknis" 7). Set via Gradle
// property / BULWARK_API_SHARED_TOKEN; empty means the header is simply omitted. Embedding a shared
// token in the APK is a demo compromise, not a production auth scheme.
val apiSharedToken: String = (project.findProperty("bulwark.apiSharedToken") as String?)
    ?: System.getenv("BULWARK_API_SHARED_TOKEN")
    ?: ""

android {
    namespace = "id.bulwark"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.bulwark"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "API_SHARED_TOKEN", "\"$apiSharedToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
