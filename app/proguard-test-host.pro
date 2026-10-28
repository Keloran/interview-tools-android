# AndroidJUnitRunner calls this shared dependency from the instrumentation APK.
# Keep its public entry point only in the explicitly requested release test host.
-keep class androidx.tracing.Trace { *; }
