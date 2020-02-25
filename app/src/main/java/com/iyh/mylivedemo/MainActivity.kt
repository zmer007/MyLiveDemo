package com.iyh.mylivedemo

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.iyh.ksylivestream.BaseCameraActivity
import com.ksyun.media.streamer.util.device.DeviceInfoTools
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
  lateinit var roomNumber: EditText

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    setSupportActionBar(toolbar)
    roomNumber = findViewById(R.id.roomNumberEditText)

    DeviceInfoTools.getInstance().init(this)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_settings -> true
      else -> super.onOptionsItemSelected(item)
    }
  }

  fun onHostClicked(view: View) {
    DeviceInfoTools.getInstance().getDeviceInfo()
    val config = BaseCameraActivity.BaseStreamConfig()
    if (roomNumber.text.isEmpty()) {
      Toast.makeText(this, "Please input Room ID", Toast.LENGTH_SHORT).show()
      return
    }
    config.mUrl = "$baseUrl/${roomNumber.text}"
    BaseCameraActivity.startActivity(this, config, BaseCameraActivity::class.java)
  }

  fun onAudienceClicked(view: View) {}

  companion object {
    const val baseUrl = "rtmp://39.105.96.172/live/livestream"
  }
}
