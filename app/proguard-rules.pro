# Xposed 入口
-keep class com.aaabbbaaa.aaabbbaaa.** { *; }

# Xposed 框架
-keep class de.robv.android.xposed.** { *; }
-keep interface de.robv.android.xposed.** { *; }

# System / Contextual Search
-keep class com.android.server.SystemServer { *; }
-keep class com.android.server.contextualsearch.** { *; }
-keep class android.app.contextualsearch.** { *; }

# ColorOS / Oplus
-keep class com.oplus.systemui.** { *; }

# 反射 & Binder 稳定性
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes Signature

# Xposed 模块建议关闭
-dontoptimize
-dontshrink