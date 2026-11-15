import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.phonegolf.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.phonegolf.app"
        // 28 = Android 9: first version that reports KEYGUARD_HIDDEN (unlock) usage events.
        minSdk = 28
        targetSdk = 36
        // The release workflow sets the two version values from the git tag.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
        // Override at build time: ./gradlew assembleRelease -PapiBaseUrl=https://golf.example.com
        val apiBaseUrl = (project.findProperty("apiBaseUrl") as String?) ?: "https://phonegolf.makersundays.com"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    // The release key is not in git. `.secrets/keystore.properties` in the root
    // of the repository gives its location and passwords. Without the file, a
    // release build has no signature.
    val keystorePropsFile = rootProject.file("../.secrets/keystore.properties")
    if (keystorePropsFile.exists()) {
        val keystoreProps = Properties().apply { load(keystorePropsFile.inputStream()) }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file("../" + keystoreProps["storeFile"])
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // 10.0.2.2 is the host machine as seen from the Android emulator.
            val debugUrl = (project.findProperty("apiBaseUrl") as String?) ?: "http://10.0.2.2:3100"
            buildConfigField("String", "API_BASE_URL", "\"$debugUrl\"")
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
