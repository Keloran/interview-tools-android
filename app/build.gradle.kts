import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import tools.interviews.gradle.InferTestKeepRules
import com.android.tools.r8.tracereferences.TraceReferences

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "tools.interviews.android"
    compileSdk {
        version = release(37)
    }

    signingConfigs {
        create("release") {
            // Local release verification produces an unsigned APK without production credentials.
            val keystorePath = System.getenv("KEYSTORE_FILE")

            if (keystorePath != null) storeFile = file(keystorePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "tools.interviews.android"
        minSdk = 26
        targetSdk = 36
        versionCode = project.findProperty("versionCode")?.toString()?.toIntOrNull() ?: 1
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Flags.gg configuration
        buildConfigField("String", "FLAGS_PROJECT_ID", "\"198ba0bd-e7e1-4219-beee-9bd82de0e03c\"")
        buildConfigField("String", "FLAGS_AGENT_ID", "\"f019ceaa-101f-4931-8740-b93d9a623b62\"")
        buildConfigField("String", "FLAGS_ENVIRONMENT_ID", "\"644f2f1d-b5d8-4c31-9c53-66d7fb59d6f2\"")
    }

    buildTypes {
        release {
            signingConfig = if (providers.gradleProperty("verifyRelease").orNull == "true") signingConfigs.getByName("debug")
                else signingConfigs.getByName("release").takeIf { it.storeFile != null }
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
        }
    }
    testBuildType = providers.gradleProperty("instrumentedBuildType").orElse("debug").get()

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work)
    // Keep WorkManager and the AndroidX test runtime on the same futures API.
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.window)
    implementation(libs.androidx.swiperefresh)
    implementation(libs.kizitonwose.calendar.view)
    implementation(libs.app.update)
    implementation(libs.app.update.ktx)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Lifecycle & Coroutines
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    // Clerk Authentication
    implementation(libs.clerk.api)
    implementation(libs.clerk.ui)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.arch.core.testing)

    // Instrumented Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // Flags
    implementation(libs.flags)
}
// AGP does not include androidTest references while shrinking the target APK.
// Infer those entry points with the R8 tool bundled with this AGP version.
if (providers.gradleProperty("verifyRelease").orNull == "true") {
    androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
        variant.androidTest?.let { tests ->
            val rules = tasks.register<InferTestKeepRules>("inferReleaseTestKeepRules") {
                tracerClasspath.from(files(TraceReferences::class.java.protectionDomain.codeSource.location))
                bootClasspath.from(androidComponents.sdkComponents.bootClasspath)
                outputFile.set(layout.buildDirectory.file("generated/test-entrypoints/release.pro"))
            }
            variant.artifacts.forScope(ScopedArtifacts.Scope.ALL).use(rules).toGet(
                ScopedArtifact.CLASSES, InferTestKeepRules::getApplicationJars, InferTestKeepRules::getApplicationDirectories)
            tests.artifacts.forScope(ScopedArtifacts.Scope.ALL).use(rules).toGet(
                ScopedArtifact.CLASSES, InferTestKeepRules::getTestJars, InferTestKeepRules::getTestDirectories)
            variant.proguardFiles.add(rules.flatMap { it.outputFile })
        }
    }
}
