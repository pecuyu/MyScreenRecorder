package com.yu.screenrecorder;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;


public class RecordService extends Service {
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;

    private boolean isRecording;
    private int width = 720;
    private int height = 1080;
    private int dpi;

    RecordReceiver recordReceiver;
    NotificationManager manager;

    Handler recordHandler ;
    @Override
    public IBinder onBind(Intent intent) {
        return new RecorderControllerImpl();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.e("TAG", "RecordService onCreate");
        HandlerThread recordThread = new HandlerThread("service_thread",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        recordThread.start();
        recordHandler = new Handler(recordThread.getLooper());
        isRecording = false;
        mediaRecorder = new MediaRecorder();

        registerRecordReceiver();
    }

    /** 设置允许应用程序捕获屏幕内容和/或记录系统音频的一个标记 */
    public void setMediaProject(MediaProjection project) {
        mediaProjection = project;
    }

    public boolean isRecording() {
        return isRecording;
    }

    /** 配置宽高以及分辨率 */
    public void setConfig(int width, int height, int dpi) {
        this.width = width;
        this.height = height;
        this.dpi = dpi;
    }

    /**
     * 初始化Recorder
     */
    private void initRecorder() {
        // 设置音频源
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        // 设置视频源
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        // 设置输出文件的格式
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        // 设置输出文件
        mediaRecorder.setOutputFile(getSaveDirectory() + System.currentTimeMillis() + ".mp4");
        // 设置视频的宽高
        mediaRecorder.setVideoSize(width, height);
        // 设置音频编码器
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        // 设置视频编码器
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        // 设置视频编码比特率
        mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);// Call this method before prepare().
        // 设置要捕捉的视频帧率
        mediaRecorder.setVideoFrameRate(30); // Must be called after setVideoSource(). Call this after setOutFormat() but before prepare()
        try {
            mediaRecorder.prepare(); // Prepares the recorder to begin capturing and encoding data
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 开始录制
     * @return 成功返回true
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public boolean startRecord() {
        if (mediaProjection == null || isRecording) {
            return false;
        }

        initRecorder();
        createVirtualDisplay();
        mediaRecorder.start();
        isRecording = true;
        sendRecordingNotification();
        return true;
    }


    /**
     * 结束录制
     * @return 成功返回 true
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public boolean stopRecord() {
        if (!isRecording) {
            return false;
        }
        cancelRecordingNotification();
        isRecording = false;
        mediaRecorder.stop();
        mediaRecorder.reset();
        virtualDisplay.release();
        mediaProjection.stop();
        LogUtil.e("TAG", "stopRecord()");
        return true;
    }

    /**
     * 释放MediaRecorder资源
     * <br/>call this method after {@link #stopRecord()}
     *  @see #stopRecord()
     */
    private void releaseRecorder() {
        if (mediaRecorder != null) mediaRecorder.release();
    }

    /**
     * 当开始录屏（{@link #startRecord()}被调用）的时候发送录屏通知
     * 在通知上可以点击停止录屏
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void sendRecordingNotification() {
        LogUtil.e("TAG", "sendRecordingNotification()");
        manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        /* 发送广播的意图 */
        Intent i = new Intent();
        i.setAction("com.yu.screenrecorder.RecordService.action_stop_record");
        PendingIntent pi = PendingIntent.getBroadcast(RecordService.this, 1, i, PendingIntent.FLAG_CANCEL_CURRENT);

        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.item_view_remote);
        remoteViews.setOnClickPendingIntent(R.id.id_btn_stop_noti, pi); // 给停止按钮添加点击事件
        remoteViews.setTextViewText(R.id.id_tv_title_noti, "正在录制...");
        Notification noti = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)  // 记得设置icon，不然通知发送无效
                .setContent(remoteViews)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .build();
        startForeground(1, noti);
        manager.notify(1, noti);
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private void cancelRecordingNotification() {
        if (manager != null) {
            stopForeground(true);
            manager.cancel(1);
        }
    }


    /**
     * 注册广播
     * <br/> 注意在恰当的时候取消注册,调用{@link #unregisterRecordReceiver()}
     */
    private void registerRecordReceiver() {
        recordReceiver = new RecordReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.yu.screenrecorder.RecordService.action_stop_record");
        registerReceiver(recordReceiver, filter);
    }

    private void unregisterRecordReceiver() {
        if (recordReceiver != null) unregisterReceiver(recordReceiver);
    }


    /** 创建一个虚拟显示来捕捉屏幕的内容 */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void createVirtualDisplay() {
        virtualDisplay = mediaProjection.createVirtualDisplay("MainScreen", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(), null, null);
    }



    /**
     * 获取保存目录，不存在则创建
     *
     * @return 成功则返回存储目录，失败返回null
     */
    public String getSaveDirectory() {
        /* SD卡是否挂载 */
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            String savedDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "ScreenRecord" + "/";

            File file = new File(savedDir);
            if (!file.exists()) {   /* 不存在则创建 */
                if (!file.mkdirs()) {
                    return null;
                }
            }

            Toast.makeText(getApplicationContext(), "savedDir=" + savedDir, Toast.LENGTH_LONG).show();
            return savedDir;
        }

        return null;
    }

    /**
     * 控制器
     */
    private class RecorderControllerImpl extends Binder implements IRecorderController {

        @Override
        public void startRecord() {
            recordHandler.post(new Runnable() {
                @Override
                public void run() {
//                    LogUtil.e("TAG", Thread.currentThread().getName().toString());
                    RecordService.this.startRecord();
                }
            });
        }

        @Override
        public boolean stopRecord() {
            return RecordService.this.stopRecord();
        }

        @Override
        public void setConfig(int width, int height, int dpi) {
            RecordService.this.setConfig(width, height, dpi);
        }

        @Override
        public boolean isRecording() {
            return RecordService.this.isRecording();
        }

        @Override
        public void setMediaProject(MediaProjection project) {
            RecordService.this.setMediaProject(project);
        }
    }

    /**
     * 处理控制录制的广播
     */
    class RecordReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case "com.yu.screenrecorder.RecordService.action_stop_record":
                    if (isRecording) stopRecord();
                    cancelRecordingNotification();
                    break;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecord();
        releaseRecorder();
        unregisterRecordReceiver();
        cancelRecordingNotification();
    }


    interface OnRecorderStateChange {
        void onStart();

        void onStop();

    }

}