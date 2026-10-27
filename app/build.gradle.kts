plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.stocktin"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.stocktin"
        minSdk = 28
        targetSdk = 35
        // En CI cada build tiene un número mayor, así Android acepta instalarlo encima.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.${versionCode}"
    }

    // La clave de firma no está en el repo: GitHub Actions la arma desde los secrets
    // STOCKTIN_KEYSTORE_BASE64 y STOCKTIN_KEYSTORE_PASSWORD (ver .github/workflows/build.yml).
    // Sin esas variables se usa la clave de debug, y ese APK no se instala encima del oficial.
    val releaseKeystore = System.getenv("STOCKTIN_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("stocktin") {
                storeFile = releaseKeystore
                storeType = "PKCS12"
                storePassword = System.getenv("STOCKTIN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("STOCKTIN_KEY_ALIAS") ?: "stocktin"
                keyPassword = System.getenv("STOCKTIN_KEYSTORE_PASSWORD")
            }
        }
    }
    val signing = signingConfigs.findByName("stocktin") ?: signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = signing
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    // org.json de verdad: el de android.jar en los tests locales es solo un esqueleto.
    testImplementation(libs.json)
}
