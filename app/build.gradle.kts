plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jfdedit3.bytrolauncher"
    compileSdk = 35

    val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
    val signingFile = System.getenv("X_LOCAL_KEYSTORE_FILE")
    val signingPassword = System.getenv("X_LOCAL_KEYSTORE_PASSWORD")
    val hasSigning = !signingFile.isNullOrBlank() && !signingPassword.isNullOrBlank()

    signingConfigs {
        if (hasSigning) {
            create("privateRelease") {
                storeFile = file(signingFile!!)
                storePassword = signingPassword
                keyAlias = "xlocalapi"
                keyPassword = signingPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.jfdedit3.bytrolauncher"
        minSdk = 24
        targetSdk = 35
        versionCode = 10000 + ciRun
        versionName = "3.0.$ciRun"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("privateRelease")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
