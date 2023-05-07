#!/usr/bin/env python3

from os import _exit
from subprocess import Popen, PIPE
from zeroconf import ServiceBrowser, ServiceListener, Zeroconf

class MyListener(ServiceListener):

    def do_stuff(self, zc: Zeroconf, type_: str, name: str) -> None:
        info = zc.get_service_info(type_, name)
        ip_bytes = info.addresses[0]
        ip_str = f"{ip_bytes[0]}.{ip_bytes[1]}.{ip_bytes[2]}.{ip_bytes[3]}"
        print(f"Found: {ip_str}:{info.port}")
        pipe = Popen(['adb', 'connect', f"{ip_str}:{info.port}"], stdout=PIPE)
        output = pipe.communicate()[0].decode("utf-8")
        pipe.wait()
        if output.startswith("connected"):
            print(output)
            zeroconf.close()
            _exit(0)

    def add_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        self.do_stuff(zc, type_, name)
    
    def update_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        self.do_stuff(zc, type_, name)
 
    def remove_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        return


zeroconf = Zeroconf()
listener = MyListener()
ServiceBrowser(zeroconf, "_adb-tls-connect._tcp.local.", listener)
ServiceBrowser(zeroconf, "_adb_secure_connect._tcp.local.", listener)

try:
    input("Waiting for a device, press Enter to abort...")
except KeyboardInterrupt:
    pass
finally:
    zeroconf.close()
