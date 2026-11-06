import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // AGP is already on the build classpath (root project), so no version here.
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidLibrary {
        namespace = "com.co.recipes.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    // iosX64 (Intel simulator) is intentionally omitted: Compose Multiplatform 1.12.x
    // no longer publishes iosX64 artifacts.
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    // Browser target (Kotlin/Wasm + Skiko). `binaries.executable()` is what generates a
    // JS entry point for `main()` and the `wasmJsBrowserDevelopmentRun` /
    // `wasmJsBrowserDistribution` tasks; the bundle is called composeApp.js because
    // src/wasmJsMain/resources/index.html loads it by that name.
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
            // Running commonTest in a browser would require karma + headless Chrome on
            // every `check`; the web build is verified through the dev server instead.
            testTask {
                enabled = false
            }
        }
        binaries.executable()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.coil.okhttp)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)

            implementation(libs.coil.compose)
            implementation(libs.kotlinx.datetime)

            // Remember-me preferences and the legacy plaintext session lookup.
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.noarg)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.coil.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.coil.ktor)
        }
        wasmJsMain.dependencies {
            // Ktor's fetch-based engine: supabase-kt and Coil both create an HttpClient.
            implementation(libs.ktor.client.js)
            // window/document/localStorage for the entry point and WebSecureStore.
            implementation(libs.kotlinx.browser)
            implementation(libs.coil.ktor)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.co.recipes.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.co.recipes"
            packageVersion = "1.0.0"
        }
    }
}
