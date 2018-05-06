package com.linhao;

import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
 * Created by haoshenglin on 2018/5/5.
 */

public class FfmpegDecodeVideoActivity extends AppCompatActivity {


    static {
        System.load("decode");
    }

    @BindView(R.id.start_decode)
    Button startDecode;
    @BindView(R.id.decode_info)
    TextView devodeInfo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ffmpeg_decode_video);
        ButterKnife.bind(this);
    }


    public native int decode(String inputurl, String outputurl);


    public native String avcodecinfo();


    @OnClick({R.id.start_decode, R.id.decode_info})
    public void onViewClicked(View view) {
        int id = view.getId();
        if (id == R.id.start_decode) {

            startDecode();
        } else if (id == R.id.decode_info) {
            devodeInfo.setText(avcodecinfo().toString());
        }
    }


    private void startDecode() {
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                String inputurl = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.mp4";
                String outputurl = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.yuv";
                decode(inputurl, outputurl);
                emitter.onNext("解码完成");
            }
        }).observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {

                        Toast.makeText(FfmpegDecodeVideoActivity.this, s, Toast.LENGTH_SHORT).show();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {

                        Log.i("decodeError", "==error==" + throwable.getMessage());
                        Toast.makeText(FfmpegDecodeVideoActivity.this, "解码失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
