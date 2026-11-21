/*
 * OpenVPN engine module.
 *
 * Builds the vendored ics-openvpn sources (https://github.com/schwabe/ics-openvpn, GPLv2,
 * commit in upstream/UPSTREAM_COMMIT) as an Android *library* using its "skeleton" UI-less
 * variant and the OpenVPN 2.x engine only (no OpenVPN3/SWIG needed).
 */
plugins {
    alias(libs.plugins.android.library)
}

val upstream = "upstream/src"

android {
    namespace = "de.blinkt.openvpn"
    compileSdk = 37
    ndkVersion = "30.0.14904198"

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        // Values the upstream code reads from BuildConfig
        buildConfigField("boolean", "openvpn3", "false")
        buildConfigField("String", "FLAVOR", "\"skeleton\"")
        externalNativeBuild {
            cmake {
                arguments += listOf("-DFGV_SKELETON=ON", "-DANDROID_STL=c++_static")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("$upstream/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("$upstream/main/AndroidManifest.xml")
            java.directories.addAll(listOf("$upstream/main/java", "$upstream/skeleton/java", "src/main/java"))
            kotlin.directories.addAll(listOf("$upstream/main/java", "$upstream/skeleton/java"))
            aidl.directories.add("$upstream/main/aidl")
            res.directories.addAll(listOf("$upstream/main/res", "$upstream/skeleton/res"))
            assets.directories.add("$upstream/main/assets")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
}
