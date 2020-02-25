package com.iyh.ksylivestream;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.google.gson.GsonBuilder;
import com.ksyun.media.streamer.encoder.VideoEncodeFormat;
import com.ksyun.media.streamer.filter.imgtex.ImgTexFilterBase;
import com.ksyun.media.streamer.filter.imgtex.ImgTexFilterMgt;
import com.ksyun.media.streamer.kit.KSYStreamer;
import com.ksyun.media.streamer.kit.StreamerConstants;
import com.ksyun.media.streamer.util.device.DeviceInfo;
import com.ksyun.media.streamer.util.device.DeviceInfoTools;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static com.ksyun.media.streamer.capture.CameraCapture.FACING_BACK;
import static com.ksyun.media.streamer.kit.StreamerConstants.ENCODE_METHOD_HARDWARE;
import static com.ksyun.media.streamer.kit.StreamerConstants.ENCODE_METHOD_SOFTWARE;
import static com.ksyun.media.streamer.kit.StreamerConstants.VIDEO_RESOLUTION_360P;

/**
 * Base streaming activity.
 */

public class BaseCameraActivity extends Activity implements
    ActivityCompat.OnRequestPermissionsResultCallback {
  public static final String TAG = "BaseCameraActivity";

  protected static final int PERMISSION_REQUEST_CAMERA_AUDIOREC = 1;
  protected static final String START_STREAM = "开始直播";
  protected static final String STOP_STREAM = "停止直播";

  protected GLSurfaceView mGLSurfaceView;
  protected Chronometer mChronometer;
  protected ImageView mFlashView;
  protected TextView mUrlTextView;
  protected TextView mStreamingText;
  protected TextView mDebugInfoTextView;

  protected BaseStreamConfig mConfig;
  protected boolean mIsLandscape;
  protected boolean mIsFlashOpened;
  protected boolean mStreaming;
  protected boolean mIsChronometerStarted;
  protected String mDebugInfo = "";
  protected boolean mHWEncoderUnsupported;
  protected boolean mSWEncoderUnsupported;

  protected KSYStreamer mStreamer;
  protected Handler mMainHandler;
  protected Timer mTimer;

  protected String mSdcardPath = Environment.getExternalStorageDirectory().getPath();
  protected String mLogoPath = "file://" + mSdcardPath + "/test.png";
  protected String mBgImagePath = "assets://bg.jpg";

  public static class BaseStreamConfig {
    public String mUrl;
    public int mCameraFacing = FACING_BACK;
    public float mFrameRate = 15.0f;
    public int mVideoKBitrate = 800;
    public int mAudioKBitrate = 48;
    public int mTargetResolution = VIDEO_RESOLUTION_360P;
    public int mOrientation = SCREEN_ORIENTATION_PORTRAIT;
    public int mEncodeMethod = isHw264EncoderSupported() ? ENCODE_METHOD_HARDWARE : ENCODE_METHOD_SOFTWARE;
    public boolean mAutoStart = false;
    public boolean mShowDebugInfo = true;

    public BaseStreamConfig fromJson(String json) {
      return new GsonBuilder().create().fromJson(json, this.getClass());
    }

    public String toJson() {
      return new GsonBuilder().create().toJson(this);
    }

    boolean isHw264EncoderSupported() {
      DeviceInfo deviceInfo = DeviceInfoTools.getInstance().getDeviceInfo();
      if (deviceInfo != null) {
        Log.i(TAG, "deviceInfo:" + deviceInfo.printDeviceInfo());
        return deviceInfo.encode_h264 == DeviceInfo.ENCODE_HW_SUPPORT;
      }
      return false;
    }
  }

  public static void startActivity(Context context, BaseStreamConfig config, Class<?> cls) {
    Intent intent = new Intent(context, cls);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.putExtra("config", config.toJson());
    context.startActivity(intent);
  }

  protected BaseStreamConfig getConfig(Bundle bundle) {
    return new BaseStreamConfig().fromJson(bundle.getString("config"));
  }

  protected int getLayoutId() {
    return R.layout.base_camera_activity;
  }

  protected void setDisplayPreview() {
    mStreamer.setDisplayPreview(mGLSurfaceView);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    setContentView(getLayoutId());
    initViews();
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    // 4.4以上系统，自动隐藏导航栏
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    mMainHandler = new Handler();
    mStreamer = new KSYStreamer(this);
    mConfig = getConfig(getIntent().getExtras());
    initUI();
    config();
    enableBeautyFilter();
    showWaterMark();

    // 是否自动开始推流
    if (mConfig.mAutoStart) {
      startStream();
    }
    // 是否显示调试信息
    if (mConfig.mShowDebugInfo) {
      startDebugInfoTimer();
    }
  }

  private void initViews() {
    mGLSurfaceView = findViewById(R.id.gl_surface_view);
    mChronometer = findViewById(R.id.chronometer);
    mFlashView = findViewById(R.id.flash);
    mUrlTextView = findViewById(R.id.url);
    mStreamingText = findViewById(R.id.start_stream_tv);
    mDebugInfoTextView = findViewById(R.id.debug_info);
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      // 4.4以上系统，自动隐藏导航栏
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
      }
    }
  }

  protected void initUI() {
    // empty here
  }

  @SuppressLint("SourceLockedOrientationActivity")
  protected void config() {
    // 设置推流URL地址
    if (!TextUtils.isEmpty(mConfig.mUrl)) {
      mUrlTextView.setText(mConfig.mUrl);
      mStreamer.setUrl(mConfig.mUrl);
    }

    // 设置推流分辨率
    mStreamer.setPreviewResolution(mConfig.mTargetResolution);
    mStreamer.setTargetResolution(mConfig.mTargetResolution);

    // 设置编码方式（硬编、软编）
    mStreamer.setEncodeMethod(mConfig.mEncodeMethod);
    // 硬编模式下默认使用高性能模式(high profile)
    if (mConfig.mEncodeMethod == ENCODE_METHOD_HARDWARE) {
      mStreamer.setVideoEncodeProfile(VideoEncodeFormat.ENCODE_PROFILE_HIGH_PERFORMANCE);
    }

    // 设置推流帧率
    if (mConfig.mFrameRate > 0) {
      mStreamer.setPreviewFps(mConfig.mFrameRate);
      mStreamer.setTargetFps(mConfig.mFrameRate);
    }

    // 设置推流视频码率，三个参数分别为初始码率、最高码率、最低码率
    int videoBitrate = mConfig.mVideoKBitrate;
    if (videoBitrate > 0) {
      mStreamer.setVideoKBitrate(videoBitrate * 3 / 4, videoBitrate, videoBitrate / 4);
    }

    // 设置音频码率
    if (mConfig.mAudioKBitrate > 0) {
      mStreamer.setAudioKBitrate(mConfig.mAudioKBitrate);
    }

    // 设置视频方向（横屏、竖屏）
    if (mConfig.mOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
      mIsLandscape = true;
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
      mStreamer.setRotateDegrees(90);
    } else if (mConfig.mOrientation == SCREEN_ORIENTATION_PORTRAIT) {
      mIsLandscape = false;
      setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
      mStreamer.setRotateDegrees(0);
    }

    // 选择前后摄像头
    mStreamer.setCameraFacing(mConfig.mCameraFacing);

    // 设置预览View
    setDisplayPreview();
    // 设置回调处理函数
    mStreamer.setOnInfoListener(mOnInfoListener);
    mStreamer.setOnErrorListener(mOnErrorListener);
    // 禁用后台推流时重复最后一帧的逻辑（这里我们选择切后台使用背景图推流的方式）
    mStreamer.setEnableRepeatLastFrame(false);
  }

  protected void handleOnResume() {
    // 调用KSYStreamer的onResume接口
    mStreamer.onResume();
    // 停止背景图采集
    mStreamer.stopImageCapture();
    // 开启摄像头采集
    startCameraPreviewWithPermCheck();
    // 如果onPause中切到了DummyAudio模块，可以在此恢复
    mStreamer.setUseDummyAudioCapture(false);
  }

  protected void handleOnPause() {
    // 调用KSYStreamer的onPause接口
    mStreamer.onPause();
    // 停止摄像头采集，然后开启背景图采集，以实现后台背景图推流功能
    mStreamer.stopCameraPreview();
    mStreamer.startImageCapture(mBgImagePath);
    // 如果希望App切后台后，停止录制主播端的声音，可以在此切换为DummyAudio采集，
    // 该模块会代替mic采集模块产生静音数据，同时释放占用的mic资源
    mStreamer.setUseDummyAudioCapture(true);
  }

  @Override
  protected void onResume() {
    super.onResume();
    handleOnResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    handleOnPause();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    // 清理相关资源
    if (mMainHandler != null) {
      mMainHandler.removeCallbacksAndMessages(null);
      mMainHandler = null;
    }
    if (mTimer != null) {
      mTimer.cancel();
    }
    mStreamer.release();
  }

  //start streaming
  protected void startStream() {
    mStreamer.startStream();
    mStreamingText.setText(STOP_STREAM);
    mStreamingText.postInvalidate();
    mStreaming = true;
  }

  // stop streaming
  protected void stopStream() {
    mStreamer.stopStream();
    mStreamingText.setText(START_STREAM);
    mStreamingText.postInvalidate();
    mStreaming = false;
    stopChronometer();
  }

  protected void showWaterMark() {
    if (!mIsLandscape) {
      mStreamer.showWaterMarkLogo(mLogoPath, 0.08f, 0.04f, 0.20f, 0, 0.8f);
      mStreamer.showWaterMarkTime(0.03f, 0.01f, 0.35f, Color.WHITE, 1.0f);
    } else {
      mStreamer.showWaterMarkLogo(mLogoPath, 0.05f, 0.09f, 0, 0.20f, 0.8f);
      mStreamer.showWaterMarkTime(0.01f, 0.03f, 0.22f, Color.WHITE, 1.0f);
    }
  }

  protected void hideWaterMark() {
    mStreamer.hideWaterMarkLogo();
    mStreamer.hideWaterMarkTime();
  }

  protected void enableBeautyFilter() {
    // 设置美颜滤镜的错误回调，当前机型不支持该滤镜时禁用美颜
    mStreamer.getImgTexFilterMgt().setOnErrorListener(new ImgTexFilterBase.OnErrorListener() {
      @Override
      public void onError(ImgTexFilterBase filter, int errno) {
        Toast.makeText(getApplicationContext(), "当前机型不支持该滤镜",
            Toast.LENGTH_SHORT).show();
        mStreamer.getImgTexFilterMgt().setFilter(mStreamer.getGLRender(),
            ImgTexFilterMgt.KSY_FILTER_BEAUTY_DISABLE);
      }
    });
    // 设置美颜滤镜，关于美颜滤镜的具体说明请参见专题说明以及完整版demo
    mStreamer.getImgTexFilterMgt().setFilter(mStreamer.getGLRender(),
        ImgTexFilterMgt.KSY_FILTER_BEAUTY_PRO3);
  }

  private void startDebugInfoTimer() {
    if (mTimer == null) {
      mTimer = new Timer();
      mTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          updateDebugInfo();
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              mDebugInfoTextView.setText(mDebugInfo);
            }
          });
        }
      }, 1000, 1000);
    }
  }

  // update debug info
  private void updateDebugInfo() {
    if (mStreamer == null) return;
    String encodeMethod;
    switch (mStreamer.getVideoEncodeMethod()) {
      case ENCODE_METHOD_HARDWARE:
        encodeMethod = "HW";
        break;
      case ENCODE_METHOD_SOFTWARE:
        encodeMethod = "SW";
        break;
      default:
        encodeMethod = "SW1";
        break;
    }
    mDebugInfo = String.format(Locale.getDefault(), " " +
            "EncodeMethod=%s PreviewFps=%.2f \n " +
            "RtmpHostIP()=%s DroppedFrameCount()=%d \n " +
            "ConnectTime()=%dms DnsParseTime()=%dms \n " +
            "UploadedKB()=%d EncodedFrames()=%d \n " +
            "CurrentKBitrate=%d Version()=%s",
        encodeMethod, mStreamer.getCurrentPreviewFps(),
        mStreamer.getRtmpHostIP(), mStreamer.getDroppedFrameCount(),
        mStreamer.getConnectTime(), mStreamer.getDnsParseTime(),
        mStreamer.getUploadedKBytes(), mStreamer.getEncodedFrames(),
        mStreamer.getCurrentUploadKBitrate(), KSYStreamer.getVersion());
  }

  public void onStartStreamClick(View v) {
    if (mStreaming) {
      stopStream();
    } else {
      startStream();
    }
  }

  protected void onSwitchCamera() {
    // 切换前后摄像头
    mStreamer.switchCamera();
  }

  protected void onFlashClick(View v) {
    if (mIsFlashOpened) {
      // 关闭闪光灯
      mStreamer.toggleTorch(false);
      mIsFlashOpened = false;
    } else {
      // 开启闪光灯
      mStreamer.toggleTorch(true);
      mIsFlashOpened = true;
    }
  }

  protected void startChronometer() {
    mChronometer.setBase(SystemClock.elapsedRealtime());
    mChronometer.start();
    mIsChronometerStarted = true;
  }

  protected void stopChronometer() {
    mChronometer.setBase(SystemClock.elapsedRealtime());
    mChronometer.stop();
    mIsChronometerStarted = false;
  }

  protected void onStreamerInfo(int what, int msg1, int msg2) {
    Log.d(TAG, "OnInfo: " + what + " msg1: " + msg1 + " msg2: " + msg2);
    switch (what) {
      case StreamerConstants.KSY_STREAMER_CAMERA_INIT_DONE:
        Log.d(TAG, "KSY_STREAMER_CAMERA_INIT_DONE");
        break;
      case StreamerConstants.KSY_STREAMER_CAMERA_FACING_CHANGED:
        Log.d(TAG, "KSY_STREAMER_CAMERA_FACING_CHANGED");
        // check is flash torch mode supported
        mFlashView.setEnabled(mStreamer.getCameraCapture().isTorchSupported());
        break;
      case StreamerConstants.KSY_STREAMER_OPEN_STREAM_SUCCESS:
        Log.d(TAG, "KSY_STREAMER_OPEN_STREAM_SUCCESS");
        mStreamingText.setText(STOP_STREAM);
        startChronometer();
        break;
      case StreamerConstants.KSY_STREAMER_FRAME_SEND_SLOW:
        Log.d(TAG, "KSY_STREAMER_FRAME_SEND_SLOW " + msg1 + "ms");
        Toast.makeText(getApplicationContext(), "Network not good!",
            Toast.LENGTH_SHORT).show();
        break;
      case StreamerConstants.KSY_STREAMER_EST_BW_RAISE:
        Log.d(TAG, "BW raise to " + msg1 / 1000 + "kbps");
        break;
      case StreamerConstants.KSY_STREAMER_EST_BW_DROP:
        Log.d(TAG, "BW drop to " + msg1 / 1000 + "kpbs");
        break;
      default:
        break;
    }
  }

  protected void onStreamerError(int what, int msg1, int msg2) {
    Log.e(TAG, "streaming error: what=" + what + " msg1=" + msg1 + " msg2=" + msg2);
    switch (what) {
      case StreamerConstants.KSY_STREAMER_AUDIO_RECORDER_ERROR_START_FAILED:
      case StreamerConstants.KSY_STREAMER_AUDIO_RECORDER_ERROR_UNKNOWN:
        break;
      case StreamerConstants.KSY_STREAMER_CAMERA_ERROR_UNKNOWN:
      case StreamerConstants.KSY_STREAMER_CAMERA_ERROR_START_FAILED:
      case StreamerConstants.KSY_STREAMER_CAMERA_ERROR_EVICTED:
      case StreamerConstants.KSY_STREAMER_CAMERA_ERROR_SERVER_DIED:
        mStreamer.stopCameraPreview();
        break;
      case StreamerConstants.KSY_STREAMER_VIDEO_ENCODER_ERROR_UNSUPPORTED:
      case StreamerConstants.KSY_STREAMER_VIDEO_ENCODER_ERROR_UNKNOWN:
        handleEncodeError();
      default:
        reStreaming(what);
        break;
    }
  }

  protected void reStreaming(int err) {
    stopStream();
    mMainHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        startStream();
      }
    }, 3000);
  }

  protected void handleEncodeError() {
    int encodeMethod = mStreamer.getVideoEncodeMethod();
    if (encodeMethod == ENCODE_METHOD_HARDWARE) {
      mHWEncoderUnsupported = true;
      if (mSWEncoderUnsupported) {
        mStreamer.setEncodeMethod(
            StreamerConstants.ENCODE_METHOD_SOFTWARE_COMPAT);
        Log.e(TAG, "Got HW encoder error, switch to SOFTWARE_COMPAT mode");
      } else {
        mStreamer.setEncodeMethod(ENCODE_METHOD_SOFTWARE);
        Log.e(TAG, "Got HW encoder error, switch to SOFTWARE mode");
      }
    } else if (encodeMethod == ENCODE_METHOD_SOFTWARE) {
      mSWEncoderUnsupported = true;
      mStreamer.setEncodeMethod(StreamerConstants.ENCODE_METHOD_SOFTWARE_COMPAT);
      Log.e(TAG, "Got SW encoder error, switch to SOFTWARE_COMPAT mode");
    }
  }

  private KSYStreamer.OnInfoListener mOnInfoListener = new KSYStreamer.OnInfoListener() {
    @Override
    public void onInfo(int what, int msg1, int msg2) {
      onStreamerInfo(what, msg1, msg2);
    }
  };

  private KSYStreamer.OnErrorListener mOnErrorListener = new KSYStreamer.OnErrorListener() {
    @Override
    public void onError(int what, int msg1, int msg2) {
      onStreamerError(what, msg1, msg2);
    }
  };

  protected void startCameraPreviewWithPermCheck() {
    int cameraPerm = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
    int audioPerm = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
    if (cameraPerm != PackageManager.PERMISSION_GRANTED ||
        audioPerm != PackageManager.PERMISSION_GRANTED) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        Log.e(TAG, "No CAMERA or AudioRecord permission, please check");
        Toast.makeText(getApplicationContext(), "No CAMERA or AudioRecord permission, please check",
            Toast.LENGTH_LONG).show();
      } else {
        String[] permissions = {Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE};
        ActivityCompat.requestPermissions(this, permissions,
            PERMISSION_REQUEST_CAMERA_AUDIOREC);
      }
    } else {
      mStreamer.startCameraPreview();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                         @NonNull int[] grantResults) {
    switch (requestCode) {
      case PERMISSION_REQUEST_CAMERA_AUDIOREC: {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          mStreamer.startCameraPreview();
        } else {
          Log.e(TAG, "No CAMERA or AudioRecord permission");
          Toast.makeText(getApplicationContext(), "No CAMERA or AudioRecord permission",
              Toast.LENGTH_LONG).show();
        }
        break;
      }
    }
  }
}
