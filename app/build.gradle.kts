import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream

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
    namespace = "com.ashareai.app.standalone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ashareai.app.standalone"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        release {
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
        getByName("androidTest").assets.srcDirs("schemas")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
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
