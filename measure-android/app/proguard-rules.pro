# ARCore uses reflection / JNI internally.
-keep class com.google.ar.** { *; }
-dontwarn com.google.ar.**
