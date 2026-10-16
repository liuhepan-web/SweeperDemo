import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    signingConfigs {
        getByName("debug") {
            storeFile = file("/Users/fire/Desktop/key/Again")
            storePassword = "276211"
            keyAlias = "key0"
            keyPassword = "276211"
        }
    }
    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localProps.load(localPropsFile.inputStream())
    }
    val keystorePath = localProps.getProperty("KEYSTORE_FILE", "")
    if (keystorePath.isNotBlank()) {
        signingConfigs {
            getByName("debug") {
                storeFile = file(keystorePath)
                storePassword = localProps.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = localProps.getProperty("KEY_ALIAS", "key0")
                keyPassword = localProps.getProperty("KEY_PASSWORD", "")
            }
        }
    }

    namespace = "com.sweeper.demo.set"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sweeper.demo.set"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        val properties = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            properties.load(localFile.inputStream())
        }
        manifestPlaceholders["TUYA_SMART_APPKEY"] = properties.getProperty("appKey", "")
        manifestPlaceholders["TUYA_SMART_SECRET"] = properties.getProperty("appSecret", "")
    }

    packaging {
        jniLibs {
            pickFirsts += listOf(
                "lib/*/libc++_shared.so",
                "lib/*/libyuv.so",
                "lib/*/libopenh264.so",
                "lib/*/liblog.so",
                "lib/*/libv8wrapper.so",
                "lib/*/libv8android.so"
            )
        }
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
            pickFirsts += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

configurations.all {
    exclude(group = "com.thingclips.smart", module = "thingsmart-modularCampAnno")
}

dependencies {
    implementation("com.ipc.demo:base:1.0.0")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // https://developer.tuya.com/cn/docs/app-development/Smart-Sweep-SDK-integrated?id=Kdjvz86ribf9l
    implementation(libs.thingsmart.sweeper)
    // P2P is based on IPC P2P: https://developer.tuya.com/cn/docs/app-development/android-sweeper-p2p?id=Kb2uowd98x17e
    implementation(libs.thingsmart.ipcsdk)
    implementation(libs.lz4.java)

    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
