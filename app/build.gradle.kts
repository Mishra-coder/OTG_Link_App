plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.otgon.keeper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.otgon.keeper"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    testOptions {
        // Vendor's enum constants build ComponentNames; let android.jar stubs no-op in tests.
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
