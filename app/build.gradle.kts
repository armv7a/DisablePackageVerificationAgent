plugins {
    id("com.android.application")
}

val releaseSigningNames = listOf(
    "releaseStoreFile", "releaseStorePassword", "releaseKeyAlias", "releaseKeyPassword"
)
val releaseSigning = releaseSigningNames.associateWith { providers.gradleProperty(it).orNull }
val hasReleaseSigning = releaseSigning.values.all { !it.isNullOrEmpty() }
require(hasReleaseSigning || releaseSigning.values.all { it == null }) {
    "Release signing requires all four properties: " + releaseSigningNames.joinToString()
}

android {
    namespace = "com.example.disableverificationagent"
    compileSdk = 35
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.example.disableverificationagent"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getValue("releaseStoreFile")!!)
                storePassword = releaseSigning.getValue("releaseStorePassword")
                keyAlias = releaseSigning.getValue("releaseKeyAlias")
                keyPassword = releaseSigning.getValue("releaseKeyPassword")
            }
        }
    }
    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging.resources.merges += "META-INF/xposed/*"
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.0")
}
