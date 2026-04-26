
plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.ankushp.paylink"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.ankushp.paylink"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        vectorDrawables { 
            useSupportLibrary = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }
    
    composeOptions {
        // This specific version (1.4.7) is required to match your Kotlin 1.8.21 environment
        kotlinCompilerExtensionVersion = "1.4.7" 
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Jetpack Compose BOM & Core Libraries
    val composeBom = platform("androidx.compose:compose-bom:2023.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.7.2")
    
    // Compose ViewModel integration (Required for UpiViewModel)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
}
