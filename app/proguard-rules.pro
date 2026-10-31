# 编译期注解，运行期不需要（Tink / security-crypto 引入）
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.jetbrains.annotations.**

# Room 生成的实现类
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }

# Compose 运行时反射极少，保留枚举 valueOf
-keepclassmembers enum * { *; }
