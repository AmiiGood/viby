import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// La clave de firma y su contrasena viven en local.properties, que esta en
// .gitignore: nunca se suben al repo. Si faltan, la release cae a la clave de
// depuracion para que el proyecto siga compilando en cualquier maquina, pero
// ESA build no podra actualizar una instalacion firmada con la clave buena.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
}
val vibyKeystore: String? = localProps.getProperty("viby.keystore")

android {
    namespace = "com.sweetcode.viby"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.sweetcode.viby"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (vibyKeystore != null) {
            create("release") {
                storeFile = file(vibyKeystore)
                storePassword = localProps.getProperty("viby.keystore.password")
                keyAlias = localProps.getProperty("viby.key.alias")
                keyPassword = localProps.getProperty("viby.key.password")
            }
        }
    }

    buildTypes {
        debug {
            // La build de pruebas se instala al lado de la Viby "de verdad" en vez de
            // chocar con ella: la release se firma con la clave de depuracion de la
            // maquina donde se compilo, asi que instalar desde otra maquina fallaria
            // con INSTALL_FAILED_UPDATE_INCOMPATIBLE y obligaria a desinstalar.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 desactivado: NewPipe/jaudiotagger usan reflexión y se romperían al ofuscar.
            // El salto de fluidez viene de que la release NO es debuggable.
            isMinifyEnabled = false
            // Clave propia de release (ver local.properties). Es la unica forma de
            // poder actualizar la app en el futuro: la clave de depuracion la genera
            // Android Studio y es distinta en cada maquina, asi que perderla deja la
            // app sin poder actualizarse nunca mas.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // NewPipeExtractor llama a URLEncoder.encode(String, Charset), que solo
        // existe desde Android 13: en Android 12 o anterior la busqueda de
        // descargas reventaba con NoSuchMethodError.
        //
        // Tiene que ser la variante _nio del desugaring, no la normal: comprobado
        // con dexdump, la normal deja la llamada intacta y solo la _nio la
        // reescribe a Lj$/net/URLEncoder, que se empaqueta dentro del APK.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.newpipeextractor)
    implementation(libs.okhttp)
    implementation(libs.jaudiotagger)
    implementation(libs.glance.appwidget)
    implementation(libs.androidx.palette)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}