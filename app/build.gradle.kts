plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.gongkao.cuotifupan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gongkao.cuotifupan"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 启用资源压缩时的保留规则
        resourceConfigurations += listOf("zh", "zh-rCN", "en") // 只保留中文和英文资源
        
        // NDK 配置 - 只指定支持的 ABI
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // 签名配置 - Debug 和 Release 使用相同的 keystore
    signingConfigs {
        create("release") {
            // 从 local.properties 读取签名信息
            val keystoreFile = project.findProperty("keystore.file") as String?
                ?: "D:\\chrome\\anzhuozhengshu"
            
            val keystorePassword = project.findProperty("keystore.password") as String?
            
            val aliasName = project.findProperty("key.alias") as String?
                ?: "key0"
            
            val aliasPassword = project.findProperty("key.password") as String?
                ?: keystorePassword
            storeFile = file("D:\\chrome\\anzhuozhengshu")
            storePassword = "Qx1314521..."
            keyPassword = "Qx1314521..."
            keyAlias = "key0"

            // 只有当密码不为空时才配置签名
            if (!keystorePassword.isNullOrBlank()) {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                keyAlias = aliasName
                keyPassword = aliasPassword ?: keystorePassword
            }
        }
    }

    buildTypes {
        release {
            // 如果配置了签名，则使用；否则使用默认签名
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig != null && releaseSigningConfig.storeFile != null) {
                signingConfig = releaseSigningConfig
            }
            // 启用代码混淆和优化
            isMinifyEnabled = true
            // 启用资源压缩
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug 也使用相同的签名配置（如果配置了），确保签名一致
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig != null && releaseSigningConfig.storeFile != null) {
                signingConfig = releaseSigningConfig
            }
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    
    // 打包选项：移除未使用的资源
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    
    // 指定 NDK 版本（使用 NDK 25 以避免 Paddle Lite 兼容性问题）
    // NDK 25 与 Paddle Lite 库兼容
    ndkVersion = "25.1.8937393"
    
    // C++ 原生代码构建配置
    // 注意：PaddleOCR 已被禁用，C++ 代码暂时不需要编译
    // 如果将来需要启用 PaddleOCR，可以取消下面的注释
    /*
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    */
}

dependencies {
    // Paddle Lite Java API
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // ML Kit Common (ML Kit 基础库，所有 ML Kit 功能都需要)
    implementation(libs.mlkit.common)
    
    // ML Kit OCR (用于手写识别)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    
    // ML Kit Digital Ink Recognition (在线手写识别，基于笔画轨迹)
    // 注意：中文支持通过语言标签指定，不需要单独的依赖包
    implementation(libs.mlkit.digital.ink)
    
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    
    // Image loading
    implementation(libs.coil)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    
    // Activity & Fragment
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    
    // UI
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.viewpager2)
    
    // PhotoView for image zoom
    implementation(libs.photoview)
    
    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    // 只在debug版本使用日志拦截器
    debugImplementation(libs.okhttp.logging)
    implementation(libs.gson)
    
    // TensorFlow Lite (用于 TrOCR 模型推理)
    implementation(libs.tensorflow.lite)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}