package com.linhao;

import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by haoshenglin on 2018/5/4.
 */

public class FfmpegPlayVideoActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    @BindView(R.id.surface_view)
    SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private String path;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ffmpeg_play_video);
        ButterKnife.bind(this);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        path = Environment.getExternalStorageDirectory().getAbsolutePath() + '/' + "test.mp4";

    }

    static {
        System.loadLibrary("VideoPlayer");
    }

    public static native int play(Object surface, String videoPath);

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {

                play(surfaceHolder.getSurface(), path);
                emitter.onNext("播放结束");
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {

                        Toast.makeText(FfmpegPlayVideoActivity.this, s, Toast.LENGTH_SHORT).show();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.e("ffmpegError==", "error===" + throwable.getMessage());
                        Toast.makeText(FfmpegPlayVideoActivity.this, "播放错误: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}
