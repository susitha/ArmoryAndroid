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
        // Chainway ships its RFID/UHF SDK as a local .aar (see app/libs/README.md) —
        // no Maven repository needed for it.
    }
}

rootProject.name = "ArmoryApp"
include(":app")
