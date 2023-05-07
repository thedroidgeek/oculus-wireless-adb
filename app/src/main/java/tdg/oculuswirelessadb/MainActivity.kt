package tdg.oculuswirelessadb

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress


class MainActivity : AppCompatActivity() {

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var switch: Switch? = null
    private var textView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switch = findViewById<Switch>(R.id.switch1)
        textView = findViewById<TextView>(R.id.textview1)

        Thread(Runnable {

            for (permission in listOf(Manifest.permission.WRITE_SECURE_SETTINGS, Manifest.permission.READ_LOGS)) {
                if (applicationContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    this@MainActivity.runOnUiThread {
                        val builder = AlertDialog.Builder(this)
                        builder.setTitle("Permission required")
                        builder.setMessage("The ${permission.split('.').last()} permission is required in order to properly enable wireless ADB - run the following adb command from a computer to enable it:\n\nadb shell pm grant ${applicationContext.getPackageName()} ${permission}")
                        builder.setCancelable(false)
                        builder.setPositiveButton(android.R.string.yes) { _, _ -> finish() }
                        builder.show()
                    }
                    return@Runnable
                }
            }

            switch!!.setOnCheckedChangeListener { _, isChecked ->
                Settings.Global.putInt(
                    applicationContext.contentResolver,
                    "adb_wifi_enabled",
                    if (isChecked) 1 else 0
                )
                updateAdbStatus()
            }

            textView!!.setOnLongClickListener {
                if (switch!!.isChecked)
                {
                    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("text", "adb connect 127.0.0.1:${textView!!.text.split(':').last()}")
                    clipboardManager.setPrimaryClip(clipData)

                    val toast: Toast = Toast.makeText(this, "ADB connect command was copied to clipboard", Toast.LENGTH_LONG)
                    val v = toast.view!!.findViewById<View>(android.R.id.message) as TextView
                    v.setTextColor(Color.BLACK) // workaround for dark mode bug
                    toast.show()

                    val launchIntent = packageManager.getLaunchIntentForPackage("com.termux")
                    launchIntent?.let { startActivity(it) }
                }
                return@setOnLongClickListener true
            }

        }).start()
    }

    override fun onResume() {
        super.onResume()
        updateAdbStatus()
    }

    @SuppressLint("SetTextI18n")
    private fun updateAdbStatus() {
        val adbWifiEnabled = Settings.Global.getInt(
            applicationContext.contentResolver,
            "adb_wifi_enabled"
        )
        if (adbWifiEnabled == 1) {
            switch?.isChecked = true
            val inputString = "logcat -d -s adbd".runCommand()
            if (inputString != null) {
                val regex = Regex("adb wifi started on port (\\d+)")
                val matches = regex.findAll(inputString)
                if (matches.count() > 0) {
                    this@MainActivity.runOnUiThread {
                        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                        val longIp = wm.connectionInfo.ipAddress.toLong()
                        val byteIp = BigInteger.valueOf(longIp).toByteArray().reversedArray()
                        val strIp = InetAddress.getByAddress(byteIp).hostAddress
                        textView?.text = "$strIp:${matches.last().groupValues[1]}"
                    }
                }
            }
        }
        else {
            switch?.isChecked = false
            textView?.text = "ADB Wireless disabled"
        }
    }

    private fun String.runCommand(): String? {
        return try {
            val parts = this.split("\\s".toRegex())
            val proc = ProcessBuilder(*parts.toTypedArray())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
            proc.inputStream.bufferedReader().readText()
        } catch(e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
