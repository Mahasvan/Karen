import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mahasvan.karen"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mahasvan.karen"
        // 31 is the floor for dynamic colour and the system_accent* resources, which is the
        // only platform feature the UI actually depends on.
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                // Keep rules live in src/main/keepRules/*.keep.
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            // LiteRT dlopens libLiteRtDispatch_*.so by path out of nativeLibraryDir, so it
            // must be a real extracted file rather than a compressed APK entry.
            useLegacyPackaging = true
        }
        resources {
            // Duplicate across ADK's transitive google-auth / api-common / httpclient jars.
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.litertlm.android)
    // kxml2 leaks in via ADK's KMP publication for its JVM target; the Android
    // variant never references it (verified: no org.xmlpull refs in the AARs)
    // and it collides with the platform's org.xmlpull classes under R8.
    listOf(
        libs.google.adk.core.android,
        libs.google.adk.openai.android,
    ).forEach { dep ->
        implementation(dep) { exclude(group = "net.sf.kxml", module = "kxml2") }
    }
    implementation(libs.commonmark)
    implementation(libs.richtext)
    implementation(libs.richtext.ui)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.reflect)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
