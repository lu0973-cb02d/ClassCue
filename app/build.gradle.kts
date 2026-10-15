import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningFile.exists()) {
        releaseSigningFile.inputStream().use(::load)
    }
}

fun releaseProperty(name: String, environmentName: String): String? =
    releaseSigningProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(environmentName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = releaseProperty("storeFile", "CLASSCUE_RELEASE_STORE_FILE")
val releaseStorePassword = releaseProperty("storePassword", "CLASSCUE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseProperty("keyAlias", "CLASSCUE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseProperty("keyPassword", "CLASSCUE_RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

// Optional stable key for the published test APKs. CI restores it from repository secrets so every
// release shares one certificate and can update the previous build in place. Without it, Android
// Gradle Plugin falls back to a machine-local debug key that differs on every CI runner.
fun environmentValue(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
val stableApkStoreFile = environmentValue("CLASSCUE_APK_STORE_FILE")
val stableApkStorePassword = environmentValue("CLASSCUE_APK_STORE_PASSWORD")
val stableApkKeyAlias = environmentValue("CLASSCUE_APK_KEY_ALIAS")
val stableApkKeyPassword = environmentValue("CLASSCUE_APK_KEY_PASSWORD")
val stableApkSigningConfigured = listOf(
    stableApkStoreFile,
    stableApkStorePassword,
    stableApkKeyAlias,
    stableApkKeyPassword
).all { it != null }

android {
    namespace = "com.classcue.kexing"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.classcue.kexing"
        // The current APK is optimized for modern arm64 Android devices.
        minSdk = 33
        targetSdk = 36
        versionCode = 16
        versionName = "0.2.7"
        ndk { abiFilters += "arm64-v8a" }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        getByName("debug") {
            if (stableApkSigningConfigured) {
                storeFile = file(requireNotNull(stableApkStoreFile))
                storePassword = requireNotNull(stableApkStorePassword)
                keyAlias = requireNotNull(stableApkKeyAlias)
                keyPassword = requireNotNull(stableApkKeyPassword)
            }
        }
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Screenshot tests render Compose screens with Robolectric; they are slower and
                // download an Android runtime, so they only run with -Pscreenshots.
                if (!project.hasProperty("screenshots")) test.exclude("**/*ScreenshotTest*")
                providers.gradleProperty("robolectricRepo").orNull?.let {
                    test.systemProperty("robolectric.dependency.repo.url", it)
                }
                project.findProperty("screenshotDir")?.let { test.systemProperty("classcue.screenshotDir", it.toString()) }
            }
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.pickFirsts += "**/libc++_shared.so"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation(files("libs/sherpa-onnx-1.13.5.aar"))
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("commons-io:commons-io:2.18.0")
    implementation("com.huawei.hms:wearengine:5.0.3.304")
    testImplementation("junit:junit:4.13.2")
    // Same Han-Latin rules as android.icu, so name-pronunciation tests run on the JVM.
    testImplementation("com.ibm.icu:icu4j:74.2")
    // Runs the history SQL (migration, lesson upserts, search) against a real SQLite engine.
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
    // Real org.json for JVM tests (android.jar only has stubs that throw).
    testImplementation("org.json:json:20240303")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.17")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
