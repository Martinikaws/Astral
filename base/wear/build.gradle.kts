import java.util.Properties

// The Wear OS remote manager for the phone's app over the Wearable Data Layer. Same application
// id and signature as the phone's app: the Data Layer only links those.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.myspoti.wear"
    compileSdk = 37

    val version = (findProperty("appVersionName") as String?) ?: "1.0.8"
    val beta = findProperty("appVersionName") == null || version.contains("-beta")
    defaultConfig {
        // As the phone's app (base/app): betas "app.astral.beta", releases "app.astral", debug builds ".dev".
        applicationId = if (beta) "app.astral.beta" else "app.astral"
        minSdk = 30
        targetSdk = 36
        versionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 210
        versionName = version
    }
    // The phone's release key (CI env, else base/keystore/keystore.properties, as base/app): the Data
    // Layer only links a watch and phone app signed alike. Without it, the debug key (local testing).
    val keystoreFile = rootProject.file("keystore/keystore.properties")
    val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }
    val envStore = System.getenv("MYSPOTI_KEYSTORE")
    val hasReleaseKey = envStore != null || keystoreFile.exists()
    // As base/app: a tagged release (stable or beta, given -PappVersionName) is never signed with the debug key.
    // Local builds (no version) and -PoverDev are not tagged releases.
    val taggedRelease = findProperty("appVersionName") != null && findProperty("overDev") == null
    if (taggedRelease && !hasReleaseKey && gradle.startParameter.taskNames.any { Regex("(assemble|bundle|package|install)Release").containsMatchIn(it) })
        throw GradleException("A tagged release needs the release key: set MYSPOTI_KEYSTORE (CI) or add keystore/keystore.properties.")
    signingConfigs {
        create("release") {
            if (envStore != null) {
                storeFile = file(envStore)
                storePassword = System.getenv("MYSPOTI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MYSPOTI_KEY_ALIAS")
                keyPassword = System.getenv("MYSPOTI_KEY_PASSWORD")
            } else if (keystoreFile.exists()) {
                storeFile = rootProject.file("keystore/${keystore.getProperty("storeFile")}")
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".dev" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
        // A release build (R8, not debuggable: the speed of the shipped app) that still pairs with the
        // phone's debug build (".dev", debug key): what to try the watch on, a debug build lags.
        create("fast") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation("androidx.wear.compose:compose-foundation:1.7.1")
    // Wear's Material 3 (scaffold, pager, lists, time text) and Google's Horologist audio UI (Apache-2.0): volume indicator.
    implementation("androidx.wear.compose:compose-material3:1.7.1")
    implementation("com.google.android.horologist:horologist-audio-ui-material3:0.8.4-alpha")
    implementation("com.google.android.horologist:horologist-compose-layout:0.8.4-alpha")
    // Core icons only (PlayArrow, Refresh); the rest are drawn in WatchIcons (the extended set is ~60 MB).
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    // Screenshots of the watch screen on the computer (WatchScreenshotTest): Robolectric draws them, Roborazzi saves them.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.1")
    testImplementation(platform(libs.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
