# Avoid direct usage of android.os.SystemProperties

Do not use android.os.SystemProperties directly. The usage of SystemProperties in PhFlags are legacy
code and should use DeviceConfig instead. For adding new PhFlags, use DebugFlags
(service-core/java/com/android/adservices/service/DebugFlags.java) for testing and
DeviceConfigFlagsHelper(service-core/java/com/android/adservices/service/DeviceConfigFlagsHelper.java)
for production.