# Keep the app's own classes readable in crash reports.
-keepattributes SourceFile,LineNumberTable
# Services and components named in the manifest are kept by default; keep the model classes as well,
# since entries are saved and read back by field name.
-keep class com.bolkhata.app.Entry { *; }
-keep class com.bolkhata.app.ChatMessage { *; }
-keep enum com.bolkhata.app.** { *; }
