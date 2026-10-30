# The rules and data model contain no reflection-based serialization, so the
# default Android optimizations are sufficient. Keep accessibility service
# entry points and the Application class if shrinking is enabled later.
-keep class com.adguardskipper.AdGuardSkipperApp { *; }
-keep class com.adguardskipper.service.AdSkipAccessibilityService { *; }
