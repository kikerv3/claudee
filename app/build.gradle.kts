plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kikerv.dirspace"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kikerv.dirspace"
        minSdk = 26
        targetSdk = 35
        // Única fuente de la verdad: el mismo fichero que dispara la release.
        versionName = rootProject.file("VERSION").readText().trim()
        // Android sólo deja actualizar una app instalada si este número sube,
        // así que se deriva de la versión en vez de tocarlo a mano.
        versionCode = versionName!!.split(".").let { parts ->
            val part = { index: Int -> parts.getOrNull(index)?.toIntOrNull() ?: 0 }
            part(0) * 10_000 + part(1) * 100 + part(2)
        }
        resourceConfigurations += listOf("es", "en")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Permite generar un APK release firmado con la clave de debug
            // cuando no hay keystore configurado (build local / CI de prueba).
            signingConfig = signingConfigs.getByName("debug")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.documentfile)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
