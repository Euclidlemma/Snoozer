// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.hilt) apply false
    id("com.google.devtools.ksp") version "1.9.0-1.0.11" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.8.9" apply false
}