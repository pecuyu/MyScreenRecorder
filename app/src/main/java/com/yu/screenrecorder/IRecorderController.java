package com.yu.screenrecorder;

import android.media.projection.MediaProjection;

/**
 * Created by D22436 on 2017/8/16.
 * 屏幕录制控制接口
 */

public interface IRecorderController {
    /**
     * 开始录制
     */
    void startRecord();

    /**
     * 结束录制
     * @return
     */
    boolean stopRecord();

    /**
     *  设置配置信息
     * @param width
     * @param height
     * @param dpi
     */
    void setConfig(int width, int height, int dpi);

    /**
     * 是否正在录制
     * @return
     */
    boolean isRecording();

    /**
     * 设置MediaProjection
     * @param project
     */
    void setMediaProject(MediaProjection project);
}

