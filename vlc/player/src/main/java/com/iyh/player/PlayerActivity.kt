package com.iyh.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.IOException
import java.util.*

/**
 * Description:
 *
 * Created by lgd on 2020/2/25.
 */
class PlayerActivity : AppCompatActivity() {
  val USE_TEXTURE_VIEW = false
  val ENABLE_SUBTITLES = true

  var mVideoLayout: VLCVideoLayout? = null

  var mLibVLC: LibVLC? = null
  var mMediaPlayer: MediaPlayer? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_player)
    val args = ArrayList<String>()
    args.add("-vvv")
    mLibVLC = LibVLC(this, args)
    mMediaPlayer = MediaPlayer(mLibVLC)
    mVideoLayout = findViewById<VLCVideoLayout>(R.id.video_layout)
  }

  override fun onDestroy() {
    super.onDestroy()
    mMediaPlayer!!.release()
    mLibVLC!!.release()
  }

  override fun onStart() {
    super.onStart()
    mMediaPlayer!!.attachViews(mVideoLayout!!, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW)
    try {
      val url = intent.getStringExtra(urlTag)
      val media = Media(mLibVLC, Uri.parse(url))
      mMediaPlayer!!.media = media
      media.release()
    } catch (e: IOException) {
      throw RuntimeException("Invalid url video stream.")
    }
    mMediaPlayer!!.play()
  }

  override fun onStop() {
    super.onStop()
    mMediaPlayer!!.stop()
    mMediaPlayer!!.detachViews()
  }

  companion object {
    const val urlTag = "url-tag"
    fun launch(ctx: Context, url: String) {
      val i = Intent(ctx, PlayerActivity::class.java)
      i.putExtra(urlTag, url)
      ctx.startActivity(i)
    }
  }
}
