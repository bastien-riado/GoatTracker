import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

// Versioned Room schema snapshots (one JSON per DB version) live in VCS so future ALTERs ship as
// reviewed migrations and migration tests can diff against the real v1 schema.
room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.example.goattracker"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.goattracker"
        minSdk = 24
        targetSdk = 36
        // versionCode/versionName are CI-driven: the release pipeline passes -PVERSION_CODE
        // (monotonic, e.g. the GitHub Actions run number) and -PVERSION_NAME. Local/dev builds
        // fall back to 1 / "1.0". The self-update check compares versionCode, so it MUST increase
        // on every published release or Android refuses the install ("version downgrade").
        versionCode = providers.gradleProperty("VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("VERSION_NAME").orNull ?: "1.0"

        // SceneView/Filament ship native (.so) libraries per ABI. Restrict to the ABIs that
        // matter — real devices (arm64-v8a, armeabi-v7a) plus x86_64 for emulator testing — to
        // keep the APK from ballooning with x86 variants nobody ships on.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // Release signing resolves from ENV first (CI sets KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/
    // KEY_PASSWORD after decoding the keystore secret), then falls back to local.properties RELEASE_*
    // keys for local dev. Secrets are never committed. Until either source is present the release
    // build is produced UNSIGNED — R8/minify still runs so the build stays verifiable.
    // NOTE: self-update REQUIRES every published release to be signed with the SAME keystore, else
    // Android rejects the install with INSTALL_FAILED_UPDATE_INCOMPATIBLE (signature mismatch).
    val keystoreProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signingValue(envKey: String, propKey: String): String? =
        System.getenv(envKey) ?: keystoreProps.getProperty(propKey)

    val storeFilePath = signingValue("KEYSTORE_FILE", "RELEASE_STORE_FILE")
    val hasReleaseSigning = storeFilePath != null

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(storeFilePath!!)
                storePassword = signingValue("KEYSTORE_PASSWORD", "RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS", "RELEASE_KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Distinct package + name so the dev build installs ALONGSIDE the prod (release) app
            // instead of colliding with it (same applicationId + different signing key = the
            // "package already exists" / INSTALL_FAILED_UPDATE_INCOMPATIBLE the installer reports).
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "GoatTrackerDev")
        }
        release {
            resValue("string", "app_name", "GoatTracker")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
      resValues = true // per-build-type app_name (GoatTracker / GoatTrackerDev)
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    testOptions {
      unitTests {
        // Robolectric: real Android resources + Context on the JVM for Room DAO/repository tests.
        isIncludeAndroidResources = true
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Serialization JSON
  implementation(libs.kotlinx.serialization.json)

  // Room — local database (suspend/Flow APIs live in room-runtime since 2.7)
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)
  // Room on the host JVM: Robolectric supplies Context, the bundled SQLite driver replaces the
  // emulator/framework SQLite so DAO tests run as plain unit tests.
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.sqlite.bundled.jvm)

  // 3D rendering — muscle heatmap body model (SceneView, Filament backend)
  implementation(libs.sceneview)

  // HTTP client for the in-app self-update check + APK download
  implementation(libs.okhttp)

  // Health Connect — read-only body-weight sync (gated at runtime on API 28+, see
  // health/HealthConnectWeightProvider; the library's minSdk 26 is overridden in the manifest)
  implementation(libs.androidx.health.connect)

  // Material Icons Core
  implementation("androidx.compose.material:material-icons-core")
}


