# =============================================================================
# Vanishly / Gemini Eraser — ProGuard / R8 Rules
# Keeps all libraries and app classes needed at runtime so the release build
# does NOT crash on launch (the #1 cause of Play Store vs debug build crashes).
# =============================================================================

# Keep line numbers in stack traces so crashes are debuggable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures (needed by Kotlin / Gson / Retrofit style reflection).
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# =============================================================================
# KOTLIN
# =============================================================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }
-dontwarn kotlin.**

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# =============================================================================
# JETPACK COMPOSE
# =============================================================================
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Compose runtime internals use reflection
-keep class androidx.compose.runtime.** { *; }
-keep @androidx.compose.runtime.Stable class * { *; }
-keep @androidx.compose.runtime.Immutable class * { *; }

# =============================================================================
# ANDROID JETPACK / ANDROIDX
# =============================================================================
-keep class androidx.** { *; }
-dontwarn androidx.**

# ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Activity Result / Contracts
-keep class androidx.activity.result.** { *; }

# FileProvider (used for share intent)
-keep class androidx.core.content.FileProvider { *; }

# =============================================================================
# GOOGLE MOBILE ADS (AdMob)
# =============================================================================
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# AdMob mediation adapters
-keepclassmembers class * extends com.google.android.gms.ads.mediation.MediationAdapter {*;}

# =============================================================================
# GOOGLE PLAY BILLING
# =============================================================================
-keep class com.android.billingclient.** { *; }
-keepclassmembers class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# =============================================================================
# OKHTTP3 (networking)
# =============================================================================
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# okhttp platform used only on JVM and when Conscrypt / security provider available
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# =============================================================================
# COIL (image loading)
# =============================================================================
-keep class coil.** { *; }
-dontwarn coil.**

# =============================================================================
# APP — keep all application classes
# =============================================================================
-keep class com.vanishly.app.** { *; }
-keepclassmembers class com.vanishly.app.** { *; }

# Keep enums (ProcessingState, SelectionMode, etc.)
-keepclassmembers enum com.vanishly.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep data classes (DrawnStroke, GalleryImage, etc.) — used in serialization / reflection
-keepclassmembers class com.vanishly.app.** {
    public <init>(...);
    public ** component*();
    public ** copy(...);
}

# =============================================================================
# ANDROID FRAMEWORK
# =============================================================================
# Keep all Activities, Services, BroadcastReceivers, ContentProviders
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# =============================================================================
# SUPPRESS COMMON WARNINGS (3rd-party libraries that reference JVM-only APIs)
# =============================================================================
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*