plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.skettidev.pipdroid"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.skettidev.pipdroid"
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
    implementation("com.google.android.gms:play-services-maps:19.2.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
//    runtimeOnly files('libs/a.jar'; 'libs/b.jar')
//    runtimeOnly fileTree('libs') { include '*.jar' }
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity:1.7.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
