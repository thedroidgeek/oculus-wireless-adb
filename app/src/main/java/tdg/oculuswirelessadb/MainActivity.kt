package tdg.oculuswirelessadb

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.Switch
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Exception
import java.math.BigInteger
import java.net.InetAddress
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class MainActivity : AppCompatActivity() {

    @SuppressLint("UseSwitchCompatOrMaterialCode")

    private var activationSwitch: Switch? = null
    private var tcpipModeCheckBox: CheckBox? = null
    private var adbStatusTextView: TextView? = null
    private var adbDiscovery: JmDNSAdbDiscovery? = null

    private var mAdbPort: Int = 0
    private var mAdbHost = "127.0.0.1"
    private var mAdbPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        activationSwitch = findViewById(R.id.switch1)
        tcpipModeCheckBox = findViewById(R.id.checkBox1)
        adbStatusTextView = findViewById(R.id.textview1)

        mAdbPath = "${applicationInfo.nativeLibraryDir}/libadb.so"

        adbDiscovery = JmDNSAdbDiscovery(applicationContext)

        findViewById<TextView>(R.id.creditsTextView).movementMethod = LinkMovementMethod.getInstance()


        for (permission in listOf(Manifest.permission.WRITE_SECURE_SETTINGS)) {
            if (applicationContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread {
                    showDialog("Permission required", "The ${permission.split('.').last()} permission is required in order to properly enable wireless ADB - " +
                            "run the following adb command from a computer to enable it:\n\n" +
                            "adb shell pm grant ${applicationContext.packageName} $permission", true)
                }
            }
        }


        // load tcpip mode setting
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        tcpipModeCheckBox!!.isChecked = sharedPref!!.getBoolean("tcpip_mode", false)


        /*
            ADB wireless activation switch
        */

        activationSwitch!!.setOnClickListener {

            // enable/disable ADB wifi
            Settings.Global.putInt(
                applicationContext.contentResolver,
                "adb_wifi_enabled",
                if (activationSwitch!!.isChecked) 1 else 0
            )

            // disable the tcpip checkbox when ADB wifi is enabled
            tcpipModeCheckBox!!.isEnabled = !activationSwitch!!.isChecked

            // update UI to reflect ADB status
            Thread { updateAdbStatus() }.start()

            if (activationSwitch!!.isChecked)
            {
                // remember tcpip mode setting for next app launch
                with(getPreferences(Context.MODE_PRIVATE)!!.edit()) {
                    putBoolean("tcpip_mode", tcpipModeCheckBox!!.isChecked)
                    apply()
                }
            }
            else {
                // make sure to disable tcpip mode if it was previously activated
                if (tcpipModeCheckBox!!.isChecked) {
                    "$mAdbPath -s 127.0.0.1:5555 usb".runCommand()
                }
            }
        }


        /*
            Termux launch shortcut on textview long click
        */

        adbStatusTextView!!.setOnLongClickListener {
            if (activationSwitch!!.isChecked)
            {
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("text", "adb connect 127.0.0.1:$mAdbPort")
                clipboardManager.setPrimaryClip(clipData)

                showToast("ADB connect command was copied to clipboard")

                val launchIntent = packageManager.getLaunchIntentForPackage("com.termux")
                launchIntent?.let { startActivity(it) }
            }
            return@setOnLongClickListener true
        }


        /*
            ADB status update on textview click
        */

        adbStatusTextView!!.setOnClickListener {
            Thread {
                runOnUiThread{ showToast("Updating ADB status...") }
                updateAdbStatus()
            }.start()
        }


        /*
            ADB tcpip mode tutorial dialog
        */

        tcpipModeCheckBox!!.setOnClickListener {
            if (tcpipModeCheckBox!!.isChecked) {
                showDialog("ADB tcpip mode", getString(R.string.tcpip_mode_steps))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Thread { updateAdbStatus() }.start()
    }


    private val updateAdbStatusLock: Lock = ReentrantLock()
    @SuppressLint("SetTextI18n")
    private fun updateAdbStatus() = updateAdbStatusLock.withLock{

        val adbWifiEnabled = Settings.Global.getInt(
            applicationContext.contentResolver,
            "adb_wifi_enabled"
        )

        if (adbWifiEnabled == 1)
        {
            runOnUiThread {
                activationSwitch?.isChecked = true
                activationSwitch?.isEnabled = false
                adbStatusTextView?.text = "..."
            }

            CoroutineScope(Dispatchers.Main).launch {
                val result = withContext(Dispatchers.IO) {
                    adbDiscovery?.discoverLocalAdbService()
                }

                if (result != null) {
                    mAdbHost = result.first
                    mAdbPort = result.second
                    runOnUiThread { adbStatusTextView?.text = "$mAdbHost:$mAdbPort" }
                }

                runOnUiThread { activationSwitch?.isEnabled = true }

                // enable handle tcpip mode
                if (tcpipModeCheckBox!!.isChecked)
                {
                    // initial tcpip connection check
                    if (checkAdbConnection(5555)) {
                        runOnUiThread { adbStatusTextView?.text = "$mAdbHost:$mAdbPort\n$mAdbHost:5555" }
                        return@launch
                    }

                    // ADB wifi connection check
                    if (!checkAdbConnection(mAdbPort)) {
                        runOnUiThread { showToast("Please run 'adb tcpip 5555' from a computer and try again") }
                        return@launch
                    }

                    // if we're connected, try to activate tcpip mode
                    if ("$mAdbPath -s $mAdbHost:$mAdbPort tcpip 5555".runCommand()?.contains("restarting") == false) {
                        runOnUiThread { showToast("Unexpected output when trying to enable tcpip mode") }
                        return@launch
                    }

                    // delay for ADB server restart
                    Thread.sleep(2_000)

                    // second tcpip mode check
                    if (checkAdbConnection(5555)) {
                        runOnUiThread { adbStatusTextView?.text = "$mAdbHost:$mAdbPort\n$mAdbHost:5555" }
                        return@launch
                    } else {
                        runOnUiThread { showToast("Failed to check tcpip mode activation") }
                    }
                }
            }
        }
        else
        {
            runOnUiThread {
                activationSwitch?.isChecked = false
                adbStatusTextView?.text = "ADB Wireless disabled"
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun checkAdbConnection(port: Int): Boolean {
        if ("$mAdbPath connect 127.0.0.1:$port".runCommand()?.contains("connected") == true) {
            val lanIp = getLanIp()
            runOnUiThread { adbStatusTextView?.text = "$lanIp:$mAdbPort\n$lanIp:5555" }
            return true
        }
        return false
    }

    private fun String.runCommand(): String? {
        Log.d("OW-ADB", "Running command: adb ${this.substringAfter(" ")}")
        try {
            val parts = this.split("\\s".toRegex())
            val procbuild = ProcessBuilder(*parts.toTypedArray())
                .directory(filesDir)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
            procbuild.environment().apply {
                put("HOME", filesDir.path)
                put("TMPDIR", cacheDir.path)
            }
            val output = procbuild.start().inputStream.bufferedReader().readText()
            Log.d("OW-ADB", "Output: $output")
            return output
        } catch(e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun showDialog(title: String, message: String, shouldFinish: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setCancelable(false)
        builder.setPositiveButton(android.R.string.yes) { _, _ -> if (shouldFinish) finish() }
        builder.show()
    }

    private fun showDialog(title: String, message: String) {
        showDialog(title, message, false)
    }

    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun getLanIp(): String? {
        return try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val longIp = wm.connectionInfo.ipAddress.toLong()
            val byteIp = BigInteger.valueOf(longIp).toByteArray().reversedArray()
            InetAddress.getByAddress(byteIp).hostAddress
        }
        catch (e: Exception) {
            "127.0.0.1"
        }
    }
}
