pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FreeGlobalVPN"
include(":app")
// OpenVPN engine: vendored ics-openvpn (GPLv2) built as an Android library (OpenVPN 2.x only).
include(":openvpn")
