# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve Google Play Integrity classes
-keep class com.google.android.play.core.integrity.** { *; }

# Preserve WireGuard backend and config classes
-keep class com.wireguard.android.** { *; }
-keep class com.wireguard.config.** { *; }
-keep class com.wireguard.crypto.** { *; }
