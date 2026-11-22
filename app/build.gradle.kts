import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val isReleaseTask = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true) || it.contains("bundle", ignoreCase = true)
}

android {
    namespace = "com.hpp.daftree"
    compileSdk = 37

    val localPropertiesFile = rootProject.file("local.properties")
    val localProperties = Properties()
    if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += listOf(
                "**/lib/*/libgpuimage.so",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/INDEX.LIST"
            )
            pickFirsts += setOf("META-INF/DEPENDENCIES")
        }
    }

    defaultConfig {
        manifestPlaceholders += mapOf()
        testInstrumentationRunnerArguments += mapOf()
        applicationId = "com.hpp.daftree"
        minSdk = 24
        targetSdk = 36
        versionCode = 68
        versionName = "1.6.8"

        // إظهار ديالوج التحديث
        buildConfigField("boolean", "IS_MAJOR_UPDATE", "false")
        buildConfigField("int", "VERSION_CODE", versionCode.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["firebaseAppCheckDebugSecret"] =
            System.getenv("a2ebc4b0-5695-4816-91f6-d71f583de3d5") ?: ""

        vectorDrawables {
            useSupportLibrary = true
        }

        val fbAppId = localProperties.getProperty("FACEBOOK_APP_ID") ?: ""
        val fbClientToken = localProperties.getProperty("FACEBOOK_CLIENT_TOKEN") ?: ""

        manifestPlaceholders["FACEBOOK_APP_ID"] = fbAppId
        manifestPlaceholders["FACEBOOK_CLIENT_TOKEN"] = fbClientToken
    }

    val keystorePropertiesFile = rootProject.file("local.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["RELEASE_KEY_ALIAS"] as String?
            keyPassword = keystoreProperties["RELEASE_KEY_PASSWORD"] as String?
            storeFile = file("F:\\android\\key3.jks")
            storePassword = keystoreProperties["RELEASE_STORE_PASSWORD"] as String?
        }
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            isDebuggable = false
            isJniDebuggable = false
            isProfileable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            @Suppress("UnstableApiUsage")
            optimization {
                keepRules {
                    ignoreFrom("com.google.firebase:firebase-auth")
                }
            }
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isMinifyEnabled = false
            ndk {
                debugSymbolLevel = "FULL"
            }
            if (!isReleaseTask) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation(libs.constraintlayout)
    implementation(libs.activity.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    // Glide (لتحميل وعرض الصور)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")

    val room_version = "2.8.4"
    val lifecycle_version = "2.10.0"

    // Room
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")

    // ViewModel & LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version")

    // WorkManager
    val work_version = "2.11.0"
    implementation("androidx.work:work-runtime-ktx:$work_version")

    // Firebase BoM (Bill of Materials)
    implementation(platform("com.google.firebase:firebase-bom:33.14.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    // Google Sign-In & Play Services
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.android.gms:play-services-base:18.6.0")
    implementation("com.google.android.gms:play-services-tasks:18.4.1")

    // PDF, Biometric, Local Server
    implementation("com.itextpdf:itextpdf:5.5.13.4")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Security & WebKit
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.webkit:webkit:1.15.0")

    // Facebook Login فقط (تم حذف facebook-android-sdk لتقليص DEX)
    implementation("com.facebook.android:facebook-login:18.1.3")

    implementation("org.jsoup:jsoup:1.22.1")
    implementation("com.android.billingclient:billing:9.0.0")
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:feature-delivery:2.1.0")

    // مفاتيح الدول
    implementation("com.hbb20:ccp:2.7.3")
    implementation("io.michaelrocks:libphonenumber-android:9.0.22")
    implementation("com.getkeepsafe.taptargetview:taptargetview:1.15.0")

    // فعّل السطر التالي مؤقتاً عند الحاجة لتجربة App Check في وضع Debug (مع إزالة التعليق في MyApplication.java)
    // implementation("com.google.firebase:firebase-appcheck-debug")

    // Google AdMob
    implementation("com.google.android.gms:play-services-ads:24.9.0")
    implementation("com.android.installreferrer:installreferrer:2.1")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")
    implementation("com.google.android.gms:play-services-ads-identifier:18.3.0")
}
