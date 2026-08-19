plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinKapt)
}

android {
    namespace = "app.cuevanatv"
    compileSdk = 34

    signingConfigs {
        create("release") {
            storeFile = file("../release.jks")
            storePassword = "cuevana2025"
            keyAlias = "cuevanatv_key"
            keyPassword = "cuevana2025"
        }
    }

    defaultConfig {
        applicationId = "app.cuevanatv"
        minSdk = 21
        targetSdk = 34
        versionCode = 101
        versionName = "26.10.2 (GOLD)"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val supabaseUrl = project.findProperty("SUPABASE_URL") ?: System.getenv("SUPABASE_URL") ?: ""
        val supabaseAnonKey = project.findProperty("SUPABASE_ANON_KEY") ?: System.getenv("SUPABASE_ANON_KEY") ?: ""
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

        val jellyfinUrl = project.findProperty("jellyfin_url") ?: System.getenv("JELLYFIN_URL") ?: ""
        val jellyfinApiKey = project.findProperty("jellyfin_api_key") ?: System.getenv("JELLYFIN_API_KEY") ?: ""
        val jellyfinUserId = project.findProperty("jellyfin_user_id") ?: System.getenv("JELLYFIN_USER_ID") ?: ""
        buildConfigField("String", "JELLYFIN_URL", "\"$jellyfinUrl\"")
        buildConfigField("String", "JELLYFIN_API_KEY", "\"$jellyfinApiKey\"")
        buildConfigField("String", "JELLYFIN_USER_ID", "\"$jellyfinUserId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation(libs.androidx.constraintlayout)
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    implementation("jp.wasabeef:glide-transformations:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation(libs.kotlinx.coroutines.android)
    implementation("org.videolan.android:libvlc-all:3.6.0")
}
