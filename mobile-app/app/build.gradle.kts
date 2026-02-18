plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services") // ✅ ضروري لـ google-services.json
}

android {
    namespace = "com.example.vitamed"

    // اتركها مثل ما هي عندك. إذا عملت Sync error لاحقًا بدّلها إلى: compileSdk = 35
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.vitamed"
        minSdk = 24
        targetSdk = 36 // إذا خفّضت compileSdk خفّض هيدي كمان
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
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))

    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")   // ✅ أضفها هون

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.gridlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

