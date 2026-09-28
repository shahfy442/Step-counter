# Steps (accelerometer pedometer)
Build: Android Studio > open this folder > Build > Build APK(s)
       (if no Gradle wrapper: let Studio use its bundled Gradle, or run `gradle wrapper --gradle-version 8.9`)
Or: push to a GitHub repo (main branch) > Actions > Build APK > download artifact `steps-debug-apk`.
Install app-debug.apk on the phone (allow "install unknown apps").
On Nothing OS also set Battery > Steps > Unrestricted so the service is not killed.
