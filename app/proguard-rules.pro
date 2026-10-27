# Keep the app's own classes readable in crash reports.
-keepattributes SourceFile,LineNumberTable
# Services and components named in the manifest are kept by default; keep the model classes as well,
# since entries are saved and read back by field name.
-keep class com.bolkhata.app.Entry { *; }
-keep class com.bolkhata.app.ChatMessage { *; }
-keep enum com.bolkhata.app.** { *; }

# PDF text reader (pdfbox-android): loads parts by name, and has optional parts the app doesn't use.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
