from os import system, _exit
from zeroconf import ServiceBrowser, ServiceListener, Zeroconf

class MyListener(ServiceListener):
    def add_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        info = zc.get_service_info(type_, name)
        ip_bytes = info.addresses[0]
        ip_str = f"{ip_bytes[0]}.{ip_bytes[1]}.{ip_bytes[2]}.{ip_bytes[3]}"
        print(f"\nFound: {ip_str}:{info.port}")
        system(f"adb connect {ip_str}:{info.port}")
        zeroconf.close()
        _exit(0)

zeroconf = Zeroconf()
listener = MyListener()
browser = ServiceBrowser(zeroconf, "_adb_secure_connect._tcp.local.", listener)

try:
    input("Waiting for a device, press Enter to abort...")
except KeyboardInterrupt:
    pass
finally:
    zeroconf.close()
