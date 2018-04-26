package com.linhao.record;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;

/**
 * Created by haoshenglin on 2018/4/26.
 */

public class AudioTrackAdapter {

    private AudioTrack audioTrack;
    private int bufferSize;


    //初始化数据
    public void createAudioTrack() {
        bufferSize = AudioTrack.getMinBufferSize(8000,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);

        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                8000,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSize,
                AudioTrack.MODE_STREAM);
    }


    //开始播放pcm录音
    public void playAudio(String mAudioFile) {
        short[] buffer = new short[bufferSize];
        DataInputStream inputStream = null;
        try {
            inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(mAudioFile)));

            if (audioTrack == null) {
                return;
            }
            audioTrack.play();
            while (inputStream.available() > 0) {
                int i = 0;
                while (inputStream.available() > 0 && i < buffer.length) {
                    buffer[i] = inputStream.readShort();
                    i++;
                }
                // 然后将数据写入到AudioTrack中
                audioTrack.write(buffer, 0, buffer.length);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            audioTrack.stop();
            try {
                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    //释放资源
    public void release() {
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack = null;
        }
    }
}
