plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cenango.fetchcicg"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cenango.fetchcicg"
        // TODO: confirm against the C72's shipped Android version (see C72 data sheet).
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // Mirrors iOS `ApiSettings`/`#if DEBUG` — hits the dev API directly.
            buildConfigField("String", "API_BASE_URL", "\"https://fetchweapons-api.cenango.com\"")
        }
        release {
            isMinifyEnabled = false
            // Release builds resolve the real server URL at runtime via
            // ApiDetailsService (mirrors iOS `getAPIDetails` + `Session.serverURL`),
            // so this is only a fallback.
            buildConfigField("String", "API_BASE_URL", "\"https://fetchweapons-api.cenango.com\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.1")

    // Networking — mirrors the role of API.swift / API+Endpoint.swift / API+Blueprint.swift
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Chainway RFID/UHF SDK (com.rscja.deviceapi) — API_Ver20251103, from
    // https://www.chainway.net/Support/Info/10. Its own AndroidManifest.xml
    // requires minSdk 17 (we're already above that) and merges in
    // BLUETOOTH/BLUETOOTH_ADMIN/CHANGE_WIFI_STATE/ACCESS_WIFI_STATE/
    // ACCESS_NETWORK_STATE permissions automatically — those cover the SDK's
    // BLE/network reader variants, not the C72's UART module we actually use.
    implementation(files("libs/DeviceAPI_ver20251103_release.aar"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
