package com.linhao;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.linhao.recordVideo.MediaMuxerAdapter;
import com.linhao.recordVideo.RecordVideoAdapter;

import java.io.IOException;

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

public class RecordVideoActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    @BindView(R.id.start_record_video)
    Button startRecordVideo;
    @BindView(R.id.stop_record_video)
    Button stopRecordVideo;
    @BindView(R.id.surfaceView)
    SurfaceView surfaceView;
    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private MediaMuxerAdapter mediaMuxerAdapter;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_video);
        ButterKnife.bind(this);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        mediaMuxerAdapter = new MediaMuxerAdapter();
        mediaMuxerAdapter.setSurface(surfaceHolder.getSurface());
    }

    @OnClick({R.id.start_record_video, R.id.stop_record_video})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.start_record_video:
                start();
                break;
            case R.id.stop_record_video:
                stop();
                break;
        }
    }

    /**
     * 停止录制
     */
    private void stop() {
        stopCamera();
        mediaMuxerAdapter.stopMuxer();
    }

    /**
     * 开始录制
     */
    private void start() {

        Toast.makeText(this, "开始录制视频", Toast.LENGTH_SHORT).show();
        startCamera();
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                mediaMuxerAdapter.startMuxer();
                emitter.onNext("");
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {

                        Toast.makeText(RecordVideoActivity.this, "录制视频结束", Toast.LENGTH_SHORT).show();

                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.e("recordVideoerror==", "" + throwable.getMessage());
                        Toast.makeText(RecordVideoActivity.this, "录制视频出错", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    /**
     * 打开摄像头
     */
    private void startCamera() {
        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        camera.setDisplayOrientation(90);
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewFormat(ImageFormat.NV21);

        // 这个宽高的设置必须和后面编解码的设置一样，否则不能正常处理
        parameters.setPreviewSize(1920, 1080);

        try {
            camera.setParameters(parameters);
            camera.setPreviewDisplay(surfaceHolder);
            camera.setPreviewCallback(RecordVideoActivity.this);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭摄像头
     */
    private void stopCamera() {
        // 停止预览并释放资源
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera = null;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        mediaMuxerAdapter.addVideoFrameData(data);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        this.surfaceHolder = surfaceHolder;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.w("MainActivity", "enter surfaceChanged method");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.w("MainActivity", "enter surfaceDestroyed method");
        MediaMuxerAdapter.stopMuxer();
        stopCamera();
    }
}
