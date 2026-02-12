# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep terminal emulator classes accessed via reflection
-keep class com.roshank8s.androidterminal.terminal.** { *; }

# Keep service classes
-keep class com.roshank8s.androidterminal.service.** { *; }
