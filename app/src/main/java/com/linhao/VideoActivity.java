package com.linhao;

import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;


import com.linhao.video.VideoAdapter;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by haoshenglin on 2018/4/27.
 */

public class VideoActivity extends AppCompatActivity {

    @BindView(R.id.start_play)
    Button startPlay;
    @BindView(R.id.video_surfaceView)
    SurfaceView videoSurfaceView;
    @BindView(R.id.stop_play)
    Button stopPlay;
    private VideoAdapter videoAdapter;
    private String videoPath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        ButterKnife.bind(this);
        Surface surface = videoSurfaceView.getHolder().getSurface();

        videoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/testvideo.mp4";
        File file = new File(videoPath);
        if (file.exists()) {
            Log.i("file===", "exist");
        }
        Log.i("path==", "==" + videoPath);
        videoAdapter = new VideoAdapter(surface);
        videoAdapter.createVideData(videoPath);
    }


    @OnClick({R.id.start_play, R.id.stop_play})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.start_play:
                videoPlay();
                break;
            case R.id.stop_play:
                videoAdapter.setPlaying(false);
                break;
        }
    }

    //播放视频
    private void videoPlay() {

        if (videoAdapter == null) {
            return;
        }
        videoAdapter.setPlaying(true);
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {

                videoAdapter.videoPlay(videoPath);
                videoAdapter.audioPlay(videoPath);
                emitter.onNext("");
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {

                        Toast.makeText(VideoActivity.this, "播放结束", Toast.LENGTH_SHORT).show();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {

                        Log.e("playError==", "===" + throwable.getMessage());
                        Toast.makeText(VideoActivity.this, "播放出错", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoAdapter != null) {
            videoAdapter.release();
            videoAdapter = null;
        }
    }
}
