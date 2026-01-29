# Spotlight buildscript-utils proguard rules

# ServiceLoader: Keep BuildscriptParserProvider implementations
-keep class * implements com.fueledbycaffeine.spotlight.buildscript.parser.impl.BuildscriptParserProvider { *; }

# Moshi custom adapters: Keep @ToJson and @FromJson annotated methods
-keepclassmembers class com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList$GradlePathAdapter {
    @com.squareup.moshi.ToJson <methods>;
    @com.squareup.moshi.FromJson <methods>;
}
-keepclassmembers class com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList$RegexAdapter {
    @com.squareup.moshi.ToJson <methods>;
    @com.squareup.moshi.FromJson <methods>;
}
