package com.linhao;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.linhao.record.PcmToWav;
import com.linhao.record.RecordAdapter;
import com.linhao.utils.FileUtils;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {


    static {
        System.loadLibrary("native-lib");
    }

    @BindView(R.id.start_record)
    Button startRecord;
    @BindView(R.id.stop_record)
    Button stopRecord;
    @BindView(R.id.record_change)
    Button recordChange;
    private RecordAdapter recordAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        initRecord();
    }

    private void initRecord() {
        recordAdapter = new RecordAdapter();
        recordAdapter.createDefaultAudio(System.currentTimeMillis() + "");
    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    @OnClick({R.id.start_record, R.id.stop_record, R.id.record_change})
    public void onViewClicked(View view) {
        int id = view.getId();
        if (id == R.id.start_record) {
            startRecord();
        } else if (id == R.id.stop_record) {
            recordAdapter.stopRecord();
            Toast.makeText(MainActivity.this, "录音结束", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.record_change) {
            Toast.makeText(MainActivity.this, "开始转换", Toast.LENGTH_SHORT).show();
            startChangePcmToWav();
        }
    }

    //将pcm文件转换为wav文件
    private void startChangePcmToWav() {
        final String pcmPath = "/storage/emulated/0/pauseRecordDemo/pcm/1524752733065.mp3.pcm";
        final String wavPath = FileUtils.getWavFileAbsolutePath(System.currentTimeMillis() + "");
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                PcmToWav.makePCMFileToWAVFile(pcmPath, wavPath, false);
                emitter.onNext("");
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        Toast.makeText(MainActivity.this, "转换成功", Toast.LENGTH_SHORT).show();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.i("chengeerror==", "==error" + throwable.getMessage());
                        Toast.makeText(MainActivity.this, "转换失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    //开始录音
    private void startRecord() {
        if (recordAdapter == null) {
            Toast.makeText(MainActivity.this, "请初始化录音", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(MainActivity.this, "开始录音", Toast.LENGTH_SHORT).show();
        recordAdapter.checkStartRecord();
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                if (recordAdapter != null) {
                    recordAdapter.startRecord();
                    emitter.onNext("");
                }
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        Toast.makeText(MainActivity.this, "录音结束", Toast.LENGTH_SHORT).show();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.i("error===", throwable.getMessage());
                        Toast.makeText(MainActivity.this, "录音失败" + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recordAdapter != null) {
            recordAdapter.release();
            recordAdapter = null;
        }
    }
}
