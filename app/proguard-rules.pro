-keep class com.classcue.kexing.engine.** { *; }
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.sun.jna.**
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
