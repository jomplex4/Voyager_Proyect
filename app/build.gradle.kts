import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firma: primero variables de entorno (GitHub Secrets), si no, keystore.properties del repo.
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(env: String, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: ksProps.getProperty(key)

val ksFile = rootProject.file(secret("VOYAGER_KEYSTORE_FILE", "storeFile") ?: "voyager-release.jks")
val ksStorePass = secret("VOYAGER_KEYSTORE_PASSWORD", "storePassword")
val ksKeyAlias = secret("VOYAGER_KEY_ALIAS", "keyAlias")
val ksKeyPass = secret("VOYAGER_KEY_PASSWORD", "keyPassword")
val canSign = ksFile.exists() && ksStorePass != null && ksKeyAlias != null && ksKeyPass != null

android {
    namespace = "com.personal.voyager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.personal.voyager"
        minSdk = 30
        targetSdk = 35
        versionCode = 100 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) // always grows, also for the automatic builds
        versionName = "3.2"
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = ksFile
                storePassword = ksStorePass
                keyAlias = ksKeyAlias
                keyPassword = ksKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Nunca "unsigned": si no hay keystore, firma con la clave debug para que el APK sea instalable.
            signingConfig = if (canSign) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/versions/**",
                "META-INF/*.kotlin_module",
                "**/*.proto",
                "DebugProbesKt.bin",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    implementation(libs.coil.compose)
    implementation(libs.okhttp)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.newpipe.extractor)
}
