# Отключаем предупреждения R8 о слишком широких правилах (это уберет желтые ошибки в Android Studio)
-dontnote com.google.mlkit.**
-dontnote androidx.camera.**
-dontnote com.google.android.gms.**

# Полная защита классов ML Kit Barcode Scanning от вырезания JNI-методов
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }

# Полная защита компонентов CameraX
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }

# Корректная инициализация депенденси через Google Play Services
-keep class com.google.android.gms.dynamite.** { *; }
-keep class com.google.android.gms.tasks.** { *; }

# Сохранение конструкторов ViewModel для Jetpack Compose
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# Игнорируем недостающие ссылки в сторонних зависимостях
-dontwarn com.google.mlkit.**
-dontwarn androidx.camera.**
