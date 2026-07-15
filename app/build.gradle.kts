plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    // Removed the broken safe args plugin line from here permanently
}

android {
    namespace = "com.avla.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.avla.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    // NEW — google-auth-library-oauth2-http pulls in gRPC/Guava transitively,
    // which ship a duplicate META-INF/INDEX.LIST (and sometimes DEPENDENCIES).
    // This tells Gradle to just keep one copy instead of failing the merge.
    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Navigation
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    // Push notifications (direct FCM send from the app, no Cloud Functions —
    // see FcmService.kt and AvlaFirebaseMessagingService.kt)
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Image loading
    implementation(libs.glide)

    // Coroutines
    implementation(libs.coroutines.android)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    // Native System Splash Screen API Support Library
    implementation("androidx.core:core-splashscreen:1.0.1")

}