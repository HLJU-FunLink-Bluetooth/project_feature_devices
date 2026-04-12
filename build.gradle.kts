plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hlju.funlinkbluetooth.feature.devices"
    compileSdk = 37

    defaultConfig {
        minSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:nearby-runtime"))
    implementation(project(":core:preferences"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose.base)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.bundles.unit.test.base)
    testImplementation(libs.test.mockk)
}
