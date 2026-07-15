-keep class online.deepdesign.deep.data.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
}

-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
