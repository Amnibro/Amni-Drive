import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ksFile = listOfNotNull(
    System.getenv("AMNI_DRIVE_SIGNING"),
    rootProject.file("keystore.properties").path,
    "${System.getProperty("user.home")}/.config/amni/signing/android/amni-drive.properties"
).map(::File).firstOrNull { it.exists() }
val ksProp = Properties().apply { ksFile?.inputStream()?.use { load(it) } }

android {
    namespace = "com.amni.drive"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.amni.drive"
        minSdk = 26
        targetSdk = 36
        versionCode = 100
        versionName = "1.15.5"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    bundle {
        language { enableSplit = false }
        density { enableSplit = false }
        abi { enableSplit = false }
    }
    androidResources {
        noCompress += "apk"
    }
    signingConfigs {
        create("release") {
            storeFile = ksProp.getProperty("storeFile")?.let { file(it) }
            storePassword = ksProp.getProperty("storePassword", "")
            keyAlias = ksProp.getProperty("keyAlias", "")
            keyPassword = ksProp.getProperty("keyPassword", "")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = if (ksFile != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
val syncBundledAmniMap by tasks.registering {
    doLast {
        val playApk = rootProject.file("../amni-map/joyride/build/outputs/apk/release/joyride-play-signed.apk")
        val mapApk = if (playApk.exists()) playApk else rootProject.file("../amni-map/joyride/build/outputs/apk/release/joyride-release.apk")
        val mapGradle = rootProject.file("../amni-map/joyride/build.gradle.kts")
        if (mapApk.exists()) {
            val bundledDir = project.file("src/main/assets/bundled").apply { mkdirs() }
            val dstApk = File(bundledDir, "amni-map.apk")
            mapApk.copyTo(dstApk, overwrite = true)
            val md = MessageDigest.getInstance("SHA-256")
            dstApk.inputStream().use { ins ->
                val buf = ByteArray(32768)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            val sha = md.digest().joinToString("") { b -> "%02x".format(b) }
            val gText = if (mapGradle.exists()) mapGradle.readText() else ""
            val vCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gText)?.groupValues?.get(1)?.toLongOrNull() ?: 37L
            val vName = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(gText)?.groupValues?.get(1) ?: "2.14.0"
            File(bundledDir, "amni-map.json").writeText(
                "{\"packageName\":\"com.amniscient.map\",\"versionCode\":$vCode,\"versionName\":\"$vName\",\"size\":${dstApk.length()},\"sha256\":\"$sha\"}\n"
            )
        }
    }
}
tasks.named("preBuild") { dependsOn(syncBundledAmniMap) }
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")
    implementation("androidx.media:media:1.7.0")
    implementation("org.maplibre.gl:android-sdk-opengl:13.0.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
