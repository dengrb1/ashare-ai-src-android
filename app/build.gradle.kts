import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream

val appMode = providers.gradleProperty("appMode").orElse("standalone").get()
require(appMode == "connected" || appMode == "standalone") { "appMode must be connected or standalone." }
val standaloneMode = appMode == "standalone"
val xiaomiSuperIslandAppId = providers.gradleProperty("XIAOMI_SUPER_ISLAND_APP_ID").orElse("").get()

// 签名配置：keystore 与密码存于根目录 keystore.properties（已被 .gitignore 排除，不入库）。
// 该文件不存在时（如 CI/新克隆）release 构建退化为未签名，不中断构建。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val standaloneSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}
fun standaloneSigningValue(property: String, environment: String): String? =
    standaloneSigningProperties.getProperty(property)
        ?: providers.gradleProperty("standaloneSigning" + property.replaceFirstChar(Char::uppercaseChar))
            .orNull
        ?: providers.environmentVariable(environment).orNull

val standaloneSigningStoreFile = standaloneSigningValue("storeFile", "ASHARE_STANDALONE_STORE_FILE")
val standaloneSigningStorePassword = standaloneSigningValue("storePassword", "ASHARE_STANDALONE_STORE_PASSWORD")
val standaloneSigningKeyAlias = standaloneSigningValue("keyAlias", "ASHARE_STANDALONE_KEY_ALIAS")
val standaloneSigningKeyPassword = standaloneSigningValue("keyPassword", "ASHARE_STANDALONE_KEY_PASSWORD")
val standaloneSigningReady = listOf(
    standaloneSigningStoreFile,
    standaloneSigningStorePassword,
    standaloneSigningKeyAlias,
    standaloneSigningKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = if (standaloneMode) "com.ashareai.app.standalone" else "com.ashareai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = if (standaloneMode) "com.ashareai.app.standalone" else "com.ashareai.app"
        minSdk = 29
        targetSdk = if (standaloneMode) 36 else 35
        versionCode = if (standaloneMode) 4 else 3
        versionName = if (standaloneMode) "2.1.0" else "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["xiaomiSuperIslandAppId"] = xiaomiSuperIslandAppId
        manifestPlaceholders["xiaomiSuperIslandBuildTypeDebug"] = "false"
        if (!standaloneMode) {
            buildConfigField("String", "MIPUSH_APP_ID", "\"${providers.gradleProperty("MIPUSH_APP_ID").orElse("").get()}\"")
            buildConfigField("String", "MIPUSH_APP_KEY", "\"${providers.gradleProperty("MIPUSH_APP_KEY").orElse("").get()}\"")
        }
    }

    signingConfigs {
        if (standaloneSigningReady) {
            create("standaloneRelease") {
                storeFile = rootProject.file(standaloneSigningStoreFile!!)
                storePassword = standaloneSigningStorePassword
                keyAlias = standaloneSigningKeyAlias
                keyPassword = standaloneSigningKeyPassword
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = if (keystorePropertiesFile.exists()) rootProject.file(keystoreProperties.getProperty("storeFile")) else null
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["xiaomiSuperIslandBuildTypeDebug"] = "true"
        }
        release {
            manifestPlaceholders["xiaomiSuperIslandBuildTypeDebug"] = "false"
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            if (standaloneSigningReady) {
                signingConfig = signingConfigs.getByName("standaloneRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = !standaloneMode
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets {
        getByName("main").apply {
            manifest.srcFile("src/$appMode/AndroidManifest.xml")
            java.setSrcDirs(listOf(if (standaloneMode) "src/main/kotlin" else "src/main/java"))
            if (!standaloneMode) {
                java.srcDir(if (fileTree("libs") { include("MiPush_SDK_Client_*.aar") }.files.isNotEmpty()) "src/mipush/java" else "src/noMipush/java")
            }
        }
        getByName("test").java.setSrcDirs(listOf(if (standaloneMode) "src/test/kotlin" else "src/test/java"))
        getByName("androidTest").java.setSrcDirs(listOf(if (standaloneMode) "src/androidTest/kotlin" else "src/androidTest/java"))
        getByName("androidTest").assets.srcDirs("schemas")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    sourceSets {
        getByName("main").kotlin.setSrcDirs(listOf(if (standaloneMode) "src/main/kotlin" else "src/main/java"))
        getByName("test").kotlin.setSrcDirs(listOf(if (standaloneMode) "src/test/kotlin" else "src/test/java"))
        getByName("androidTest").kotlin.setSrcDirs(
            listOf(if (standaloneMode) "src/androidTest/kotlin" else "src/androidTest/java"),
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.markdown.material3)
    implementation(libs.focus.api)
    ksp(libs.androidx.room.compiler)
    if (!standaloneMode) {
        implementation(fileTree("libs") { include("MiPush_SDK_Client_*.aar") })
        implementation(libs.retrofit)
        implementation(libs.retrofit.kotlinx.serialization)
        implementation(libs.okhttp.logging)
        implementation(libs.okhttp.sse)
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
}
