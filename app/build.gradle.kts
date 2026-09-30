import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream

// 弃用 -PappMode 参数检查
if (providers.gradleProperty("appMode").isPresent) {
    logger.warn("""
        ⚠️  -PappMode 参数已弃用。
        从 3.0.0 起，单一 APK 同时支持本地和 Fusion 工作区。
        构建命令无需再指定 appMode。
    """.trimIndent())
}

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

android {
    namespace = "com.ashareai.app"  // 统一包名
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ashareai.app"  // 统一 applicationId
        minSdk = 29
        targetSdk = 36
        versionCode = 5  // 从 connected 的 3 和 standalone 的 4 升级
        versionName = "3.0.0"  // 重大版本：合并架构
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["xiaomiSuperIslandAppId"] = xiaomiSuperIslandAppId
        manifestPlaceholders["xiaomiSuperIslandBuildTypeDebug"] = "false"

        // BuildConfig 始终启用
        buildConfigField("String", "MIPUSH_APP_ID", "\"${providers.gradleProperty("MIPUSH_APP_ID").orElse("").get()}\"")
        buildConfigField("String", "MIPUSH_APP_KEY", "\"${providers.gradleProperty("MIPUSH_APP_KEY").orElse("").get()}\"")
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
        buildConfig = true  // 总是启用
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // 统一源集：同时包含 java 和 kotlin 目录
    sourceSets {
        getByName("main").apply {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.setSrcDirs(listOf("src/main/java", "src/main/kotlin"))
            // MiPush 可选集成
            if (fileTree("libs") { include("MiPush_SDK_Client_*.aar") }.files.isNotEmpty()) {
                java.srcDir("src/mipush/java")
            } else {
                java.srcDir("src/noMipush/java")
            }
        }
        getByName("test").java.setSrcDirs(listOf("src/test/java", "src/test/kotlin"))
        getByName("androidTest").apply {
            java.setSrcDirs(listOf("src/androidTest/java", "src/androidTest/kotlin"))
            assets.srcDirs("schemas")
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    sourceSets {
        getByName("main").kotlin.setSrcDirs(listOf("src/main/java", "src/main/kotlin"))
        getByName("test").kotlin.setSrcDirs(listOf("src/test/java", "src/test/kotlin"))
        getByName("androidTest").kotlin.setSrcDirs(listOf("src/androidTest/java", "src/androidTest/kotlin"))
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // 合并所有依赖：不再用 if (!standaloneMode) 判断
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

    // Fusion 工作区依赖（始终包含）
    implementation(fileTree("libs") { include("MiPush_SDK_Client_*.aar") })
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)

    // 测试依赖
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
