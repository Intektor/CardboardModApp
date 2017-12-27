package de.intektor.cardboardmodapp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.widget.MediaController
import kotlinx.android.synthetic.main.activity_cardboard.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.concurrent.thread


class CardboardActivity : AppCompatActivity() {

    lateinit var mediaController: MediaController

    private var accessory: UsbAccessory? = null
    private var aInputStream: InputStream? = null
    private var aOutputStream: OutputStream? = null

    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

    lateinit var mPermissionIntent: PendingIntent

    @Volatile
    var currentText: String = "text"

    @Volatile
    var accessoryRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cardboard)

        mPermissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0);

        mediaController = MediaController(this)

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)

        registerReceiver(mUsbReceiver, filter)

        val handler = Handler()
        object : Runnable {
            override fun run() {
                textView.text = currentText
                handler.postDelayed(this, 250)
            }
        }.run()
    }

    private fun openAccessory(accessory: UsbAccessory) {
        if (ContextCompat.checkSelfPermission(this, ACTION_USB_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            val mUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager
            mUsbManager.requestPermission(accessory, mPermissionIntent)
            return
        }

        currentText = "Opening Accessory"

        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        this.accessory = accessory

        val fd = manager.openAccessory(accessory)

        accessoryRunning = true

        if (fd != null) {
            aInputStream = FileInputStream(fd.fileDescriptor)
            aOutputStream = FileOutputStream(fd.fileDescriptor)

            thread {
                while (accessoryRunning) {
                    try {
                        val byteArray = ByteArray(8)
                        aInputStream?.read(byteArray)
                        currentText = Arrays.toString(byteArray)
                    } catch (t: Throwable) {
                        currentText = t.localizedMessage
                    }
                }
            }
        }

    }

    fun closeAccessory() {
        accessoryRunning = false
        accessory = null
        aInputStream?.close()
        aOutputStream?.close()

        currentText = "Closing Accessory"
    }

    private val mUsbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val accessory: UsbAccessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory)
                    }
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED == action) {
                val accessory: UsbAccessory? = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                if (accessory != null && accessory == this@CardboardActivity.accessory) {
                    closeAccessory()
                }
            }
        }
    }
}
