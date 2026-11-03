-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
# NewPipe uses Rhino's safe interpreted mode. The desktop scripting/optimizer
# adapters are optional on Android; keep the upstream Rhino compatibility rules.
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-keepattributes Signature,InnerClasses,EnclosingMethod

-keep class ai.onnxruntime.** { *; }
-keep class com.atilika.kuromoji.** { *; }
-keep class com.ibm.icu.** { *; }
-keepnames class id.iswara.music.data.UpdateCheckWorker
-keepnames class id.iswara.music.data.UpdateDownloadWorker
