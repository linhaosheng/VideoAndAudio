package com.linhao.video;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Created by haoshenglin on 2018/4/27.
 */

public class VideoAdapter {

    private final static String TAG = "VideoAdapter==";
    private static final long TIMEOUT_US = 10000;
    private MediaExtractor videoExtractor;
    private MediaCodec videoCodec;
    private String videoPath;
    private int videoTrackIndex;
    private IPlayerCallBack callBack;
    private Surface surface;
    private boolean isPlaying;
    private int audioInputBufferSize;
    private AudioTrack audioTrack;
    private MediaExtractor audioExtractor;


    public VideoAdapter(Surface surface) {
        this.surface = surface;
    }

    public void setCallBack(IPlayerCallBack callBack) {
        this.callBack = callBack;
    }

    public void setPlaying(boolean isPlaying) {
        this.isPlaying = isPlaying;
    }

    public void createVideData(String videoPath) {
        this.videoPath = videoPath;
        videoExtractor = new MediaExtractor();
    }

    /**
     * 播放视频
     *
     * @param videoPath
     */
    public void videoPlay(String videoPath) {
        try {
            if (videoExtractor == null) {
                videoExtractor = new MediaExtractor();
            }
            videoExtractor.setDataSource(videoPath);
            videoTrackIndex = getMediaTrackIndex(videoExtractor, "video/");
            if (videoTrackIndex >= 0) {
                MediaFormat mediaFormat = videoExtractor.getTrackFormat(videoTrackIndex);
                int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                float time = mediaFormat.getLong(MediaFormat.KEY_DURATION) / 1000000;
                //             callBack.videoAspect(width, height, time);
                videoExtractor.selectTrack(videoTrackIndex);

                videoCodec = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
                videoCodec.configure(mediaFormat, surface, null, 0);

                if (videoCodec == null) {
                    Log.e("videoerror===", "videoCode is null");
                    return;
                }

                videoCodec.start();
                MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
                ByteBuffer[] inputBuffers = videoCodec.getInputBuffers();
                boolean isVideoEOS = false;
                long startMs = System.currentTimeMillis();

                while (true) {
                    if (!isPlaying) {
                        continue;
                    }

                    //将资源传递到解码器
                    if (!isVideoEOS) {
                        isVideoEOS = putBufferToCoder(videoExtractor, videoCodec, inputBuffers);
                    }

                    int outputBufferIndex = videoCodec.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_US);
                    switch (outputBufferIndex) {
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.v(TAG, "format changed");
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Log.v(TAG, "超时");
                            break;
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            //outputBuffers = videoCodec.getOutputBuffers();
                            Log.v(TAG, "output buffers changed");
                            break;
                        default:
                            //直接渲染到Surface时使用不到outputBuffer
                            //ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                            //延时操作
                            //如果缓冲区里的可展示时间>当前视频播放的进度，就休眠一下
                            sleepRender(videoBufferInfo, startMs);
                            //渲染
                            videoCodec.releaseOutputBuffer(outputBufferIndex, true);
                            break;
                    }
                    if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.v(TAG, "buffer stream end");
                        break;
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (videoCodec != null) {
                videoCodec.stop();
                videoCodec.release();
                videoCodec = null;
            }
        }
    }


    /**
     * 播放音频
     *
     * @param audioPath
     */
    public void audioPlay(String audioPath) {

        if (audioExtractor == null) {
            audioExtractor = new MediaExtractor();
        }

        MediaCodec audioCodec = null;
        try {
            audioExtractor.setDataSource(audioPath);
            for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                MediaFormat mediaFormat = audioExtractor.getTrackFormat(i);
                String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i);
                    int audioChannels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    int audioSampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int minBufferSize = AudioTrack.getMinBufferSize(audioSampleRate,
                            (audioChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO),
                            AudioFormat.ENCODING_PCM_16BIT);
                    int maxInputSize = mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    audioInputBufferSize = minBufferSize > 0 ? minBufferSize * 4 : maxInputSize;
                    int frameSizeInBytes = audioChannels * 2;
                    audioInputBufferSize = (audioInputBufferSize / frameSizeInBytes) * frameSizeInBytes;
                    audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                            audioSampleRate,
                            (audioChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO),
                            AudioFormat.ENCODING_PCM_16BIT,
                            audioInputBufferSize,
                            AudioTrack.MODE_STREAM);
                    audioTrack.play();
                    Log.v(TAG, "audio play");
                    //
                    audioCodec = MediaCodec.createDecoderByType(mime);
                    audioCodec.configure(mediaFormat, null, null, 0);
                    break;
                }
            }
            if (audioCodec == null) {
                Log.v(TAG, "audio decoder null");
                return;
            }
            audioCodec.start();
            //
            final ByteBuffer[] buffers = audioCodec.getOutputBuffers();
            int sz = buffers[0].capacity();
            if (sz <= 0)
                sz = audioInputBufferSize;
            byte[] mAudioOutTempBuf = new byte[sz];

            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            ByteBuffer[] inputBuffers = audioCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = audioCodec.getOutputBuffers();
            boolean isAudioEOS = false;
            long startMs = System.currentTimeMillis();

            while (!Thread.interrupted()) {
                if (!isPlaying) {
                    continue;
                }
                if (!isAudioEOS) {
                    isAudioEOS = putBufferToCoder(audioExtractor, audioCodec, inputBuffers);
                }
                //
                int outputBufferIndex = audioCodec.dequeueOutputBuffer(audioBufferInfo, TIMEOUT_US);
                switch (outputBufferIndex) {
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.v(TAG, "format changed");
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.v(TAG, "超时");
                        break;
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        outputBuffers = audioCodec.getOutputBuffers();
                        Log.v(TAG, "output buffers changed");
                        break;
                    default:
                        ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                        //延时操作
                        //如果缓冲区里的可展示时间>当前视频播放的进度，就休眠一下
                        sleepRender(audioBufferInfo, startMs);
                        if (audioBufferInfo.size > 0) {
                            if (mAudioOutTempBuf.length < audioBufferInfo.size) {
                                mAudioOutTempBuf = new byte[audioBufferInfo.size];
                            }
                            outputBuffer.position(0);
                            outputBuffer.get(mAudioOutTempBuf, 0, audioBufferInfo.size);
                            outputBuffer.clear();
                            if (audioTrack != null)
                                audioTrack.write(mAudioOutTempBuf, 0, audioBufferInfo.size);
                        }
                        //
                        audioCodec.releaseOutputBuffer(outputBufferIndex, false);
                        break;
                }

                if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.v(TAG, "buffer stream end");
                    break;
                }
            }//end while
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (audioCodec != null) {
                audioCodec.stop();
                audioCodec.release();
                audioCodec = null;
            }
            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        if (videoCodec != null) {
            videoCodec.start();
            videoCodec.release();
            videoCodec = null;
        }
        if (videoExtractor != null) {
            videoExtractor.release();
            videoExtractor = null;
        }
        if (audioExtractor != null) {
            audioExtractor.release();
            audioExtractor = null;
        }

    }

    /**
     * 延时渲染
     *
     * @param bufferInfo
     * @param startMsg
     */
    private void sleepRender(MediaCodec.BufferInfo bufferInfo, long startMsg) {

        while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMsg) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    /**
     * 将缓冲区传递至解码器
     * 如果到了文件末尾，返回true;否则返回false
     *
     * @param mediaExtractor
     * @param
     * @param
     * @return
     */
    private boolean putBufferToCoder(MediaExtractor mediaExtractor, MediaCodec decoder, ByteBuffer[] inputBuffers) {

        boolean isMediaEOS = false;
        int inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
            if (sampleSize < 0) {
                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                isMediaEOS = true;
                Log.v(TAG, "media eos");
            } else {
                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, mediaExtractor.getSampleTime(), 0);
                mediaExtractor.advance();
            }
        }
        return isMediaEOS;

    }

    /**
     * 获取指定类型媒体文件所在轨道
     *
     * @param mediaExtractor
     * @param MEDIA_TYPE
     * @return
     */
    private int getMediaTrackIndex(MediaExtractor mediaExtractor, String MEDIA_TYPE) {
        int trackIndex = -1;
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
            String mime = trackFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(MEDIA_TYPE)) {
                trackIndex = i;
                break;
            }
        }
        return trackIndex;
    }
}
