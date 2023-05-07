In order to avoid manually typing the randomly generated port each time, this is an mDNS discovery script for the service types used on the Meta Quest headsets for ADB: `_adb_secure_connect._tcp.local` (Android 10) and `_adb-tls-connect._tcp.local` (Android 12).
This is done in order to automatically discover the IP and port of the spawned ADB TLS server and establish the connection.

```
$ pip3 install -r requirements.txt
[...]

$ python3 discover-and-connect.py
Waiting for a device, press Enter to abort...
Found: 192.168.1.100:73313
connected to 192.168.1.100:73313
```