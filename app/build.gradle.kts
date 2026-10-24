plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Every GitHub build gets a higher number, so the app knows when an update exists.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.bolkhata.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bolkhata.app"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.$buildNumber"
        // Built-in Gemini key from the GitHub secret GEMINI_KEY. Never written in the code.
        val geminiKey = (System.getenv("GEMINI_KEY") ?: "").replace("\"", "").trim()
        buildConfigField("String", "GEMINI_KEY", "\"$geminiKey\"")
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
            isMinifyEnabled = false
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
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
