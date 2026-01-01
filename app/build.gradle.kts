

android {
    namespace = "com.skettidev.pipdroid"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.skettidev.pipdroid"
        minSdk = 24
        targetSdk = 36
        maxSdk = 36
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
        debug {

        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity:1.7.2")
    implementation("com.daimajia.androidanimations:library:2.4@aar")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.recyclerview:recyclerview:1.3.1")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.media3.common)
    implementation(libs.recyclerview)
    implementation(libs.core.animation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
//    runtimeOnly files('libs/a.jar'; 'libs/b.jar')
//    runtimeOnly fileTree('libs') { include '*.jar' }
}
plugins {
    alias(libs.plugins.android.application) version "8.12.0"
}