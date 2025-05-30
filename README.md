# oculus-wireless-adb

An app that enables wireless ADB from within a Meta Quest VR headset.

This is done through the Android global settings provider (requires manually granting `WRITE_SECURE_SETTINGS`).

Since the ADB TLS port is random each time, mDNS discovery is used in order to detect it within the app.

From there, you can either use that port (if your ADB client was already authorized), or you can enable the old TCP mode on port 5555 (requires a computer for first set up).

### Installation commands

```
adb install app-debug.apk
adb shell pm grant tdg.oculuswirelessadb android.permission.WRITE_SECURE_SETTINGS
```

- After ADB wireless is enabled, [a python script](script/) can be used on an authorized computer to automatically discover and connect to the device (without the need for tcpip mode).

- The `tcpip` mode, which allows unauthorized connections to come through with a on-device prompt, needs a computer to set up for the first time, so that the embedded ADB client can be allowed to enable the mode by itself in the future.
  
  * This can be achieved by running the command `adb tcpip 5555` from a computer with the Quest plugged in via USB, then using this app to activate ADB with the tcpip mode option checked.
    
  * Once the embedded client is authorized, subsequent activations should work without requiring a computer.

**Note:** When using an app like Termux on the Quest for the ADB client, beware that the ADB daemon (server) only starts if it's not *already running*, so this can cause conflicts with the embedded client on Oculus Wireless ADB (if tcpip mode is activated), and the Termux one, since the authorized keys will **only** be loaded by the client that starts the ADB deamon, which can cause connection issues.

The ADB daemon can be killed using `adb kill-server`, in order to spawn it again with the right ADB client/keys.
