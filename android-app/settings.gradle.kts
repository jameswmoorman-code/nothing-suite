pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Official Glyph Developer Kit .aar dropped into ./glyph-sdk (see glyph-sdk/README.md)
        flatDir { dirs("glyph-sdk") }
    }
}
rootProject.name = "NothingSuite"
// Three separate apps sharing one design system. Separate apps = separate
// Play listings = the dialer's review isn't dragged down by microphone or
// accessibility permissions it doesn't need (and vice versa).
include(":core-design")
include(":app")                  // Dialer + AI call screener + Glyph progress tracker
include(":anti-theft-module")    // Shade Guard — biometric gate on Quick Settings while locked
include(":music-tracker-module") // Now Playing — ambient music recognition widget
