# R8-Regeln für die Release-Variante.
# Ziel: kotlinx.serialization und Retrofit überleben das Shrinking. Macht R8 Probleme,
# darf es laut Entscheidung #19 abgeschaltet werden (isMinifyEnabled = false).

# Stacktraces lesbar halten
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------- kotlinx.serialization ----------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# Companion-Objekte und serializer()-Methoden von @Serializable-Klassen behalten
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Eigene Serializable-Klassen (API-Modelle und Navigationsrouten)
-keep,includedescriptorclasses class de.immoscrabber.app.**$$serializer { *; }
-keepclassmembers class de.immoscrabber.app.** {
    *** Companion;
}
-keepclasseswithmembers class de.immoscrabber.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- Retrofit ----------
# Generische Signaturen und Annotationen der Service-Interfaces behalten
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# ---------- OkHttp ----------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
