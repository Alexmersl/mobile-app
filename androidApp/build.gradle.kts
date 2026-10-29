plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.fieldlog.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.fieldlog"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Подпись и релизная сборка в эту версию не входят: только debug.
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    // Рендер карты на Android — OpenGL (docs/plan.md).
    runtimeOnly(libs.maplibre.compose.runtime.opengl.android)
}
