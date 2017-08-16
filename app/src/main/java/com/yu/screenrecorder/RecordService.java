package com.yu.screenrecorder;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.util.Log;
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


    @Override
    public IBinder onBind(Intent intent) {
        return new RecorderControlerImpl();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e("TAG", "RecordService onCreate");
        HandlerThread serviceThread = new HandlerThread("service_thread",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        serviceThread.start();
        isRecording = false;
        mediaRecorder = new MediaRecorder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void setMediaProject(MediaProjection project) {
        mediaProjection = project;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void setConfig(int width, int height, int dpi) {
        this.width = width;
        this.height = height;
        this.dpi = dpi;
    }

    public boolean startRecord() {
        if (mediaProjection == null || isRecording) {
            return false;
        }

        initRecorder();
        createVirtualDisplay();
        mediaRecorder.start();
        isRecording = true;
        return true;
    }


    /**
     * 当开始录屏的时候发送录屏通知
     * 在通知上可以点击停止录屏
     */
    @TargetApi(Build.VERSION_CODES.N)
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void sendRecordingNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent i = new Intent();
        PendingIntent pi = PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT);
        RemoteViews view=null;
        Notification noti = new Notification.Builder(this)
                .setContentText("正在录制...")
                .setContentIntent(pi)
                .setCustomContentView(view)
                .build();
        manager.notify(1, noti);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public boolean stopRecord() {
        if (!isRecording) {
            return false;
        }
        isRecording = false;
        mediaRecorder.stop();
        mediaRecorder.reset();
        virtualDisplay.release();
        mediaProjection.stop();

        return true;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void createVirtualDisplay() {
        virtualDisplay = mediaProjection.createVirtualDisplay("MainScreen", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(), null, null);
    }

    /**
     * 初始化Recorder
     */
    private void initRecorder() {
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(getSaveDirectory() + System.currentTimeMillis() + ".mp4");
        mediaRecorder.setVideoSize(width, height);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
        mediaRecorder.setVideoFrameRate(30);
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取保存目录，不存在则创建
     *
     * @return
     */
    public String getSaveDirectory() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            String rootDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "ScreenRecord" + "/";

            File file = new File(rootDir);
            if (!file.exists()) {
                if (!file.mkdirs()) {
                    return null;
                }
            }

            Toast.makeText(getApplicationContext(), rootDir, Toast.LENGTH_SHORT).show();

            return rootDir;
        } else {
            return null;
        }
    }

    /**
     * 控制器
     */
    class RecorderControlerImpl extends Binder implements IRecorderController {

        @Override
        public void startRecord() {
            RecordService.this.startRecord();
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
}