In order to avoid manually typing the randomly generated port each time, this is an mDNS discovery script for the `_adb_secure_connect._tcp.local` service type ([renamed](https://android.googlesource.com/platform/packages/modules/adb/+/77b8ff316a62842fc006ee35772db6567b0878c7%5E%21/#F2) on newer adb binaries), which happens to be what's used on the Quest 2 at the time of writing, in order to automatically discover the IP and port of the spawned ADB TLS server and establish the connection.

```
$ pip3 install -r requirements.txt
[...]

$ python3 discover-and-connect.py
Waiting for a device, press Enter to abort...
Found: 192.168.1.100:73313
connected to 192.168.1.100:73313
```