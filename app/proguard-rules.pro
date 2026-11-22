# Preserve exact stock JNI class names and native methods bound by /system/lib/*.so
-keep class com.unisound.jni.Uni4micHalJNI {
    *;
}

-keep class cn.yunzhisheng.asrfix.JniAsrFix {
    *;
}
