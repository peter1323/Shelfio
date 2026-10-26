import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.shelfio.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shelfio.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        // Clave de Google Books del desarrollador: se lee de local.properties (que no se sube a Git).
        // Está restringida en Google Cloud a esta app Android y a la Books API.
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "GOOGLE_BOOKS_API_KEY", "\"${localProperties.getProperty("GOOGLE_BOOKS_API_KEY", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Room guarda el esquema de cada versión de la base de datos en app/schemas para poder escribir migraciones.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    // Para que Coil pueda cargar portadas desde internet (miniaturas de los resultados de búsqueda)
    implementation(libs.coil.network.okhttp)

    // Escáner ISBN: CameraX + ML Kit (modelo incluido en la app, funciona sin conexión)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)
    // Leer el título de la portada (modelo incluido en la app, sin conexión)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    // Pruebas del repositorio con Room en memoria sin móvil (Robolectric simula Android en el PC)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
