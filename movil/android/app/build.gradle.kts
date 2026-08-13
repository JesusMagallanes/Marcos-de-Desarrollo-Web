plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.backend.smartzone_movil"
    // Fijado a 36 en lugar de `flutter.compileSdkVersion` (37).
    //
    // El SDK instala esa versión como `android-37.0` —Google cambió el nombre
    // de carpeta para las revisiones menores— pero el plugin de Gradle sigue
    // buscando `android-37` a secas y el build falla con "Failed to find
    // target with hash string 'android-37'". El paquete `platforms;android-37`
    // ni siquiera existe ya en el repositorio, así que no se puede instalar.
    //
    // La 36 está instalada y es justo la que declaran los plugins que usa la
    // app, así que no se pierde nada. Se puede volver a
    // `flutter.compileSdkVersion` cuando el plugin de Gradle entienda el
    // nombre nuevo.
    compileSdk = 36
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.backend.smartzone_movil"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
