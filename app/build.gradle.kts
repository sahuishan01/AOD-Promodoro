plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.algosculptor.pomodoro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.algosculptor.pomodoro"
        minSdk = 34
        targetSdk = 35
        versionCode = 96
        versionName = "0.2.94"
    }

    signingConfigs {
        create("pomodoro") {
            val envPath = System.getenv("KEYSTORE_PATH") ?: ""
            val envPass = System.getenv("SIGNING_STORE_PASSWORD")?.takeIf { it.isNotEmpty() } ?: "***PURGED***"
            val envAlias = System.getenv("SIGNING_KEY_ALIAS")?.takeIf { it.isNotEmpty() } ?: "pomodoro"
            val envKeyPass = System.getenv("SIGNING_KEY_PASSWORD")?.takeIf { it.isNotEmpty() } ?: envPass
            val kFile = if (envPath.isNotEmpty()) file(envPath) else rootProject.file("app/keystore/release.jks")
            storeFile = kFile
            storePassword = envPass
            keyAlias = envAlias
            keyPassword = envKeyPass
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("pomodoro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("pomodoro")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
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
    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    implementation(libs.coil.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
