plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The release number: CI sets APP_VERSION to the last published release + 1, so releases go 295, 296, ...
// and the app shows the same "1.N" as the release page and the update pop-up.
val buildNumber = (System.getenv("APP_VERSION") ?: System.getenv("GITHUB_RUN_NUMBER"))?.toIntOrNull() ?: 1

android {
    namespace = "com.bolkhata.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bolkhata.app"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

    signingConfigs {
        create("fixed") {
            storeFile = file("bolkhata.jks")
            storePassword = "bolkhata123"
            keyAlias = "bolkhata"
            keyPassword = "bolkhata123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            // Shrunk and optimised: the app starts and scrolls much faster than the debug build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Needed by the screenshot tests, which render real screens with Robolectric.
            isIncludeAndroidResources = true
        }
    }
}

// Screenshot tests run only on request (gradle ... -Pscreenshots), so normal builds stay fast.
tasks.withType<Test>().configureEach {
    if (project.hasProperty("screenshots")) systemProperty("roborazzi.test.record", "true")
    else exclude("**/screenshots/**")
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Reads the text inside PDF statements on the phone, so most statements need no AI.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Bundled Latin OCR model: text recognition works without a network request at runtime.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests; the Android stub throws "not mocked".
    testImplementation("org.json:json:20240303")
    // Screenshot tests: render real Compose screens on the JVM and save them as PNG files.
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.26.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.26.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
