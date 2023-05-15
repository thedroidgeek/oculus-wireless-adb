# oculus-wireless-adb
An app that enables wireless ADB from within a Quest 2/Quest Pro VR headset.

This is done through the Android global settings provider (requires manually granting `WRITE_SECURE_SETTINGS`).

Since the TCP port is random each time, parsing is done on logcat output (`READ_LOGS` permission required) in order to display it within the app.

### Installation commands
```
adb install app-debug.apk
adb shell pm grant tdg.oculuswirelessadb android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant tdg.oculuswirelessadb android.permission.READ_LOGS
```

After ADB wireless is enabled, [a script](script/) can be used to automatically discover and connect to it with the help of the mDNS protocol.

This app now also has a `tcpip` mode, which allows unauthorized/insecure connections to come through - this however needs a computer to set up for the first time, so that the embedded ADB client can be authorized so that it can enable the mode by itself in the future.

This can be achieved by running the command `adb tcpip 5555` from a computer, then activating ADB from within the app, with the tcpip mode option checked.
