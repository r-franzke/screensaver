plugins {
    id("com.android.application")
}

// CI passes these; local builds get dev values.
val ciVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 1
val ciVersionName = providers.environmentVariable("VERSION_NAME").orNull ?: "0.0.0-dev"

// Release signing comes from the environment (set by the CI workflow from repository secrets).
val keystorePath = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull
val keystorePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
val hasSigning = listOf(keystorePath, keystorePassword, signingKeyAlias, signingKeyPassword)
    .all { !it.isNullOrEmpty() }

android {
    namespace = "app.webscreensaver.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.webscreensaver.tv"
        minSdk = 26
        targetSdk = 37
        versionCode = ciVersionCode
        versionName = ciVersionName
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Newer AGP/SDK versions appear all the time; that alone should not break CI.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
