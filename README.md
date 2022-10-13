# oculus-wireless-adb
An app that enables wireless ADB from within a Quest 2 device.

### Installation commands
```
adb install app-debug.apk
adb shell pm grant tdg.oculuswirelessadb android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant tdg.oculuswirelessadb android.permission.READ_LOGS
```
