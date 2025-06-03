plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.roadpro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.roadpro"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures{
        viewBinding = true
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.cloudinary.android)
    implementation(libs.glide.v4160)

    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.play.services.auth)

    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)

    implementation(libs.googleid)
    implementation(libs.play.services.maps)
    implementation(libs.places)

    implementation ("androidx.compose.ui:ui:1.7.8")
    implementation ("androidx.compose.material:material:1.5.0")
    implementation ("androidx.compose.material3:material3:1.3.1")
    implementation ("androidx.compose.foundation:foundation:1.5.0")
    implementation ("androidx.compose.runtime:runtime-livedata:1.7.8")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    implementation ("com.google.android.libraries.places:places:4.2.0")
    implementation ("com.google.android.libraries.places:places:3.3.0")
    implementation (libs.play.services.maps.v1820)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)


}