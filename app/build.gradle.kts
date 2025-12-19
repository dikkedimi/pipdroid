plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.pipdroid_3000_mkii"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.pipdroid_3000_mkii"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
//    runtimeOnly files('libs/a.jar', 'libs/b.jar')
//    runtimeOnly fileTree('libs') { include '*.jar' }
}
