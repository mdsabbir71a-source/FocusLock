# Methods invoked from JavaScript must retain their names after shrinking.
-keepclassmembers,allowoptimization class * {
    @android.webkit.JavascriptInterface <methods>;
}
