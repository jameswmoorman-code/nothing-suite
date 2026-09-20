pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "NothingSuite"
// Three separate apps sharing one design system. Separate apps = separate
// Play listings = the dialer's review isn't dragged down by microphone or
// accessibility permissions it doesn't need (and vice versa).
include(":core-design")
include(":core-billing")             // Play Billing one-time unlock, shared by every paid app
include(":app")                  // Dialer + AI call screener + Glyph progress tracker
include(":anti-theft-module")    // Shade Guard — biometric gate on Quick Settings while locked
include(":music-tracker-module") // Now Playing — ambient music recognition widget
include(":dot-widgets")          // Dot Widgets — dot-matrix home-screen widgets for ANY Android phone (roadmap #1)
