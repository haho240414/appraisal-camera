# Release hardening.
#
# R8 obfuscates and shrinks app code in release builds. This does not make
# copying impossible, but it makes APK/AAB reverse engineering materially harder.

-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
