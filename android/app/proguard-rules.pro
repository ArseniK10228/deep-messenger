-keep class online.deepdesign.deep.data.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
}
