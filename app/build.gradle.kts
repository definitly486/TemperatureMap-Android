plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.definitly.temperaturemap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.definitly.temperaturemap"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Убираем runtime-проверки параметров Kotlin: меньше байткода, поведение не меняется.
        freeCompilerArgs += listOf(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions"
        )
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true      // R8: удаление неиспользуемого кода (в osmdroid его много)
            isShrinkResources = true    // удаление неиспользуемых ресурсов
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Служебные файлы библиотек, не нужные в рантайме.
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/DEPENDENCIES",
                "/META-INF/*.version",
                "/META-INF/com/android/build/gradle/app-metadata.properties",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "/kotlin/**",
                "/META-INF/*.kotlin_module"
            )
        }
    }

    // Блоб с метаданными зависимостей (нужен только для Google Play).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
