package com.linhao.recordVideo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Vector;

/**
 * Created by haoshenglin on 2018/4/27.
 */

public class RecordVideoAdapter {

    public static final int IMAGE_HEIGHT = 1080;
    public static final int IMAGE_WIDTH = 1920;

    private static final String TAG = "RecordVideoAdapter";

    // 编码相关参数
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private static final int FRAME_RATE = 25; // 帧率
    private static final int IFRAME_INTERVAL = 10; // I帧间隔（GOP）
    private static final int TIMEOUT_USEC = 10000; // 编码超时时间

    // 视频宽高参数
    private int mWidth;
    private int mHeight;

    // 存储每一帧的数据 Vector 自增数组
    private Vector<byte[]> frameBytes;
    private byte[] mFrameData;

    private static final int COMPRESS_RATIO = 256;
    private static final int BIT_RATE = IMAGE_HEIGHT * IMAGE_WIDTH * 3 * 8 * FRAME_RATE / COMPRESS_RATIO; // bit rate CameraWrapper.

    private final Object lock = new Object();

    private MediaCodecInfo mCodecInfo;
    private MediaCodec mMediaCodec;  // Android硬编解码器
    private MediaCodec.BufferInfo mBufferInfo; //  编解码Buffer相关信息

    private MediaMuxerAdapter mediaMuxer; // 音视频混合器
    private MediaFormat mediaFormat; // 音视频格式

    private Surface surface;

    public volatile boolean isStart = false;
    public volatile boolean isExit = false;
    public volatile boolean isMuxerReady = false;


    public RecordVideoAdapter(int mWidth, int mHeight, Surface surface1,MediaMuxerAdapter mediaMuxerAdapter) {
        this.mediaMuxer = mediaMuxerAdapter;
        this.surface = surface1;
        this.mWidth = mWidth;
        this.mHeight = mHeight;
        prepare();
        frameBytes = new Vector<>();
    }

    //初始化数据
    private void prepare() {
        Log.i(TAG, "VideoEncoderThread().prepare");
        mFrameData = new byte[this.mWidth * this.mHeight * 3 / 2];
        mBufferInfo = new MediaCodec.BufferInfo();
        mCodecInfo = selectCodec(MIME_TYPE);
        if (mCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, this.mWidth, this.mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        int colorFormat = getColorFormat(mCodecInfo);
        Log.i("color==", colorFormat + "");
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
    }

    /**
     * 获取支持的视频颜色
     *
     * @param mediaCodecInfo
     * @return
     */
    private int getColorFormat(MediaCodecInfo mediaCodecInfo) {
        int matchedFormat = 0;
        MediaCodecInfo.CodecCapabilities codecCapabilities =
                mediaCodecInfo.getCapabilitiesForType(MIME_TYPE);
        for (int i = 0; i < codecCapabilities.colorFormats.length; i++) {
            int format = codecCapabilities.colorFormats[i];
            if (format >= codecCapabilities.COLOR_FormatYUV420Planar &&
                    format <= codecCapabilities.COLOR_FormatYUV420PackedSemiPlanar) {
                if (format >= matchedFormat) {
                    matchedFormat = format;
                    //   logColorFormatName(format);
                    break;
                }
            }
        }
        return matchedFormat;
    }

    //开始视频录制
    public void startRecordVideo() {
        while (!isExit) {
            if (!isStart) {
//                stopMediaCodec();
//                if (!isMuxerReady) {
//                    synchronized (lock) {
//                        try {
//                            Log.e(TAG, "video -- 等待混合器准备...");
//                            lock.wait();
//                        } catch (InterruptedException e) {
//                        }
//                    }
//                }
                if (isMuxerReady) {
                    try {
                        Log.e(TAG, "video -- startMediaCodec...");
                        startMediaCodec();
                    } catch (IOException e) {
                        isStart = false;
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                }

            } else if (!frameBytes.isEmpty()) {
                byte[] bytes = this.frameBytes.remove(0);
                Log.e("ang-->", "解码视频数据:" + bytes.length);
                try {
                    encodeFrame(bytes);
                } catch (Exception e) {
                    Log.e(TAG, "解码视频(Video)数据 失败");
                    e.printStackTrace();
                }
            }
        }
        Log.e(TAG, "Video 录制线程 退出...");
    }


    /**
     * 编码每一帧的数据
     *
     * @param input 每一帧的数据
     */
    private void encodeFrame(byte[] input) {
        Log.w(TAG, "VideoEncoderThread.encodeFrame()");

        // 将原始的N21数据转为I420
        NV21toI420SemiPlanar(input, mFrameData, this.mWidth, this.mHeight);

        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();

        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(mFrameData);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, mFrameData.length, System.nanoTime() / 1000, 0);
        } else {
            Log.e(TAG, "input buffer not available");
        }

        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        Log.i(TAG, "outputBufferIndex-->" + outputBufferIndex);
        do {
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaCodec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mMediaCodec.getOutputFormat();
                if (mediaMuxer != null) {
                    mediaMuxer.addTrackIndex(MediaMuxerAdapter.TRACK_VIDEO, newFormat);
                }
            } else if (outputBufferIndex < 0) {
                Log.e(TAG, "outputBufferIndex < 0");
            } else {
                Log.d(TAG, "perform encoding");
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                if (outputBuffer == null) {
                    throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex + " was null");
                }
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }
                if (mBufferInfo.size != 0) {

                    if (mediaMuxer != null && !mediaMuxer.isVideoTrackAdd()) {
                        MediaFormat newFormat = mMediaCodec.getOutputFormat();
                        mediaMuxer.addTrackIndex(MediaMuxerAdapter.TRACK_VIDEO, newFormat);
                    }
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

                    if (mediaMuxer != null && mediaMuxer.isMuxerStart()) {
                        mediaMuxer.addMuxerData(new MediaMuxerAdapter.MuxerData(MediaMuxerAdapter.TRACK_VIDEO, outputBuffer, mBufferInfo));
                    }

                    Log.d(TAG, "sent " + mBufferInfo.size + " frameBytes to muxer");
                }
                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        } while (outputBufferIndex >= 0);
    }


    /**
     * 摄像头获取的是nv21的数据类型，需要转换为i420数据
     *
     * @param nv21bytes
     * @param i420bytes
     * @param width
     * @param height
     */
    private static void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes, int width, int height) {
        System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height);
        for (int i = width * height; i < nv21bytes.length; i += 2) {
            i420bytes[i] = nv21bytes[i + 1];
            i420bytes[i + 1] = nv21bytes[i];
        }
    }

    /**
     * 停止视频编码
     */
    private void stopMediaCodec() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        isStart = false;
        Log.e(TAG, "stop video 录制...");
    }

    /**
     * 获取摄像头的数据
     *
     * @param data
     */
    public void add(byte[] data) {
        if (frameBytes != null && isMuxerReady) {
            frameBytes.add(data);
        }
    }


    /**
     * 重新录制
     */
    public synchronized void restart() {
        isStart = false;
        isMuxerReady = false;
        frameBytes.clear();
    }

    /**
     * 开始视频编码
     *
     * @throws IOException
     */
    private void startMediaCodec() throws IOException {
        mMediaCodec = MediaCodec.createByCodecName(mCodecInfo.getName());
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();
        isStart = true;
    }


    public void setMuxerReady(boolean muxerReady) {
        synchronized (lock) {
            Log.e(TAG, Thread.currentThread().getId() + " video -- setMuxerReady..." + muxerReady);
            isMuxerReady = muxerReady;
            lock.notifyAll();
        }
    }


    /**
     * 根据音频类型选择对应的编解码
     *
     * @param mimeType
     * @return
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }
}
