plugins {
    id("com.android.application")
}

android {
    namespace = "id.bulwark.fixture.hadiah"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.bulwark.fixture.hadiah"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
