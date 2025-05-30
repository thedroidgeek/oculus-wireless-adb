package tdg.oculuswirelessadb

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

class JmDNSAdbDiscovery(private val context: Context) {

    private var jmdns: JmDNS? = null
    private var serviceListener: ServiceListener? = null

    suspend fun discoverLocalAdbService(): Pair<String, Int>? = withContext(Dispatchers.IO) {
        val deferredResult = CompletableDeferred<Pair<String, Int>?>()

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lock = wifi.createMulticastLock("jmdns_multicast_lock")
            lock.setReferenceCounted(true)
            lock.acquire()

            val ip = getDeviceIpAddress()
            if (ip == null) {
                Log.e("JmDNS", "Could not determine IP address")
                deferredResult.complete(null)
                return@withContext deferredResult.await()
            }

            jmdns = JmDNS.create(ip)

            serviceListener = object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    jmdns?.requestServiceInfo(event.type, event.name, true)
                }

                override fun serviceRemoved(event: ServiceEvent) {
                    Log.d("JmDNS", "Service removed: ${event.info}")
                }

                override fun serviceResolved(event: ServiceEvent) {
                    val host = event.info.inetAddresses.firstOrNull()?.hostAddress
                    val port = event.info.port
                    if (host != null && port > 0 && !deferredResult.isCompleted) {
                        // check if it's the local device
                        if (host == getDeviceIpAddress()?.hostAddress) {
                            Log.i("JmDNS", "Found ADB: $host:$port")
                            deferredResult.complete(Pair(host, port))
                            stopDiscovery()
                        }
                    }
                }
            }

            jmdns?.addServiceListener("_adb-tls-connect._tcp.local.", serviceListener)
            jmdns?.addServiceListener("_adb_secure_connect._tcp.local.", serviceListener)
        } catch (e: Exception) {
            Log.e("JmDNS", "Discovery error", e)
            deferredResult.complete(null)
        }

        return@withContext deferredResult.await()
    }

    fun stopDiscovery() {
        try {
            serviceListener?.let {
                jmdns?.removeServiceListener("_adb-tls-connect._tcp.local.", it)
                jmdns?.removeServiceListener("_adb_secure_connect._tcp.local.", it)
            }
            jmdns?.close()
            jmdns = null
            Log.i("JmDNS", "Discovery stopped")
        } catch (e: Exception) {
            Log.e("JmDNS", "Error stopping discovery", e)
        }
    }

    private fun getDeviceIpAddress(): InetAddress? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wifiManager.connectionInfo.ipAddress
        val ipBytes = byteArrayOf(
            (ipInt and 0xff).toByte(),
            (ipInt shr 8 and 0xff).toByte(),
            (ipInt shr 16 and 0xff).toByte(),
            (ipInt shr 24 and 0xff).toByte()
        )
        return InetAddress.getByAddress(ipBytes)
    }
}
