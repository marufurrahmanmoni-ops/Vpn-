plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.moni.freeglobalvpn"
    ndkVersion = "30.0.14904198" // used to strip debug symbols from the OpenVPN libraries
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.moni.freeglobalvpn"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "1.3.0"
        // Public key that must have signed the verified server list (empty = HTTPS only). See docs.
        buildConfigField("String", "LIST_PUBLIC_KEY", "\"${providers.gradleProperty("FGV_LIST_PUBKEY").orElse("").get()}\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    signingConfigs {
        // Release signing is read from ~/.gradle/gradle.properties or local keystore.properties
        // (never commit your keystore). See BUILD.md.
        create("release") {
            val ks = providers.gradleProperty("FGV_KEYSTORE").orNull
            if (ks != null) {
                storeFile = file(ks)
                storePassword = providers.gradleProperty("FGV_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("FGV_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("FGV_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // One APK that installs on every phone (no ABI splits to pick from).
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (providers.gradleProperty("FGV_KEYSTORE").isPresent)
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // Signature schemes (AGP adds v1 only when minSdk < 24; v2/v3 are supported by every Android 7.0+ device).
    signingConfigs.configureEach {
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // OpenVPN runs as an executable (libovpnexec.so) – it must be extracted on install.
        jniLibs { useLegacyPackaging = true }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Optional: validate against a real VPN Gate list, e.g.
                //   curl -o /tmp/vpngate.csv https://www.vpngate.net/api/iphone/
                //   ./gradlew :app:testDebugUnitTest -Pvpngate.snapshot=/tmp/vpngate.csv [-Pvpngate.robolectric=true]
                providers.gradleProperty("vpngate.snapshot").orNull?.let { test.systemProperty("vpngate.snapshot", it) }
            }
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    // Real-engine test (ics-openvpn ConfigParser/VpnProfile under Robolectric): add -Pvpngate.robolectric=true
    if (providers.gradleProperty("vpngate.robolectric").isPresent) {
        sourceSets.getByName("test").java.directories.add("src/testReal/java")
        sourceSets.getByName("test").kotlin.directories.add("src/testReal/java")
    }
}

dependencies {
    implementation(project(":openvpn"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric is only pulled in for the opt-in real-data test (see testOptions above).
    if (providers.gradleProperty("vpngate.robolectric").isPresent) {
        testImplementation(libs.robolectric)
        testImplementation(libs.androidx.test.core)
    }
}
