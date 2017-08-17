package com.yu.screenrecorder;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity implements RecordService.OnRecorderStateChangeListener {
    private static final int RECORD_REQUEST_CODE = 101;
    private static final int STORAGE_REQUEST_CODE = 102;
    private static final int AUDIO_REQUEST_CODE = 103;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private Button startBtn;
    private IRecorderController mRecorderController;

    private TextView tvRecordInfo;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        setContentView(R.layout.activity_main);

        tvRecordInfo = (TextView) findViewById(R.id.id_tv_show_record_info);
        startBtn = (Button) findViewById(R.id.start_record);
        startBtn.setEnabled(false);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View v) {
                if (mRecorderController.isRecording()) {
                    mRecorderController.stopRecord();
                    startBtn.setText(R.string.start_record);
                } else {
                    /* Returns an Intent that must passed to startActivityForResult() in order to start screen capture */
                    Intent captureIntent = projectionManager.createScreenCaptureIntent();
                    /* 发送录屏请求 */
                    startActivityForResult(captureIntent, RECORD_REQUEST_CODE);
                }
            }
        });

        checkPermission(); /* 检查权限*/

      //  registerRecordReceiver();
        Intent intent = new Intent(this, RecordService.class);

        bindService(intent, conn, BIND_AUTO_CREATE);
    }

    /** 检查权限*/
    private void checkPermission() {
        /* 检查读写权限*/
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQUEST_CODE);
        }
        /* 检查录制音频权限 */
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_REQUEST_CODE);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RECORD_REQUEST_CODE && resultCode == RESULT_OK) {
            /* 获取屏幕采集的接口 */
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            mRecorderController.setMediaProject(mediaProjection);
            mRecorderController.startRecord();
            startBtn.setText(R.string.stop_record);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (conn != null) unbindService(conn);
       // unregisterRecordReceiver();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_REQUEST_CODE || requestCode == AUDIO_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                finish(); // 权限拒绝则直接结束
            }
        }
    }

    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            mRecorderController = (IRecorderController) service;
            mRecorderController.setConfig(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi);
            mRecorderController.setRecordingCallback(MainActivity.this);
            startBtn.setEnabled(true);
            startBtn.setText(mRecorderController.isRecording() ? R.string.stop_record : R.string.start_record);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };

    RecordReceiver recordReceiver;
    private void registerRecordReceiver() {
        recordReceiver = new RecordReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.yu.screenrecorder.RecordService.action_stop_record");
        registerReceiver(recordReceiver, filter);
    }

    private void unregisterRecordReceiver() {
        if (recordReceiver != null) unregisterReceiver(recordReceiver);
    }

    @Override
    public void onRecordStart() {

    }

    private String lastTime;
    @Override
    public void onRecordUpdate(final String time) {
        lastTime = time;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvRecordInfo.setText("正在录制:" + time);
            }
        });
    }

    @Override
    public void onRecordStop() {
        startBtn.setText( R.string.start_record );
        tvRecordInfo.setText("上次录制时长:"+lastTime);
    }


    class RecordReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case "com.yu.screenrecorder.RecordService.action_stop_record":
                    startBtn.setText( R.string.start_record );
                    break;
            }
        }
    }
}
