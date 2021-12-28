package com.cip.acousense;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.kaopiz.kprogresshud.KProgressHUD;
import com.shashank.sony.fancytoastlib.FancyToast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_PERMISSION = 1001;

    private String[] permissions = new String[] {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    /**被用户拒绝的权限列表*/
    private List<String> mPermissionList = new ArrayList<>();
    private AudioRecord mAudioRecord;
    private boolean isRecording;
    private Button mAudioControl;
    private Button mJson;
    private TextView attension, json, result;

    private Executor executor = Executors.newSingleThreadExecutor();
    private KerasTFLite mTFLite;
    private float[][][][] mfcc;
    private KProgressHUD hud;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "onCreate");
//        checkPermissions();

        mAudioControl = findViewById(R.id.btn_control);
        mJson = findViewById(R.id.btn_json);
        mJson.setEnabled(false);
        mAudioControl.setOnClickListener(this);
        mJson.setOnClickListener(this);

        attension = findViewById(R.id.tv);
        msgShow();
        json = findViewById(R.id.tv_json);
        result = findViewById(R.id.tv_probability);

        initPython();


    }
    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart");
    }
    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        checkPermissions();
        super.onStart();
    }
    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");
        initTensorFlowAndLoadModel();
        super.onResume();
    }

    private void msgShow(){
        attension.append(getString(R.string.message0));
        attension.append("\n"+ getString(R.string.message1));
        attension.append("\n"+ getString(R.string.message2));
        attension.append("\n"+ getString(R.string.message3));
    }

    /**
     * 开始录制
     */
    public void startRecord() {
        final int minBufferSize = AudioRecord.getMinBufferSize(Consts.SAMPLE_RATE_INHZ, Consts.CHANNEL_CONFIG, Consts.AUDIO_FORMAT);
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, Consts.SAMPLE_RATE_INHZ, Consts.CHANNEL_CONFIG
                , Consts.AUDIO_FORMAT, minBufferSize);

        final byte[] data = new byte[minBufferSize];
        final File file = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "test.pcm");
        if (!file.mkdirs()) {
            Log.e(TAG, "Directory not created");
        }
        if (file.exists()) {
            file.delete();
        }

        mAudioRecord.startRecording();
        isRecording = true;

        //TODO pcm 数据无法直接播放， 保存为wav 格式

        new Thread(new Runnable() {
            @Override
            public void run() {
                FileOutputStream os = null;
                try {
                    os = new FileOutputStream(file);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (os != null) {
                    while (isRecording) {
                        int read = mAudioRecord.read(data, 0, minBufferSize);
                        //如果读取音频数据没有出现错误， 就讲数据写入到文件
                        if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                            try {
                                os.write(data);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    try {
                        Log.i(TAG, "run: close file output stream !");
                        os.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    /***
     * 停止录制
     */
    public void stopRecord() {
        isRecording = false;
        //释放资源
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            for (int i = 0; i < grantResults.length; i ++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    FancyToast.makeText(this,"Necessary permission: "+permissions[i],FancyToast.LENGTH_LONG, FancyToast.INFO,false).show();
                    Log.i(TAG, permissions[i] + " 权限被用户禁止！");
                }
            }
        }
    }

    /***
     * 检查权限
     */
    private void checkPermissions() {
        mPermissionList.clear();
        //6.0 动态权限判断
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (int i = 0; i < permissions.length; i ++) {
                if (ContextCompat.checkSelfPermission(getApplicationContext(), permissions[i])
                        != PackageManager.PERMISSION_GRANTED) {
                    mPermissionList.add(permissions[i]);
                }
            }
            if (!mPermissionList.isEmpty()) {
                String[] permissions = mPermissionList.toArray(new String[mPermissionList.size()]);
                ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION);
            }
        }
    }

    private void pcmToWav(){
        PcmToWavUtil pcmToWavUtil = new PcmToWavUtil(Consts.SAMPLE_RATE_INHZ, Consts.CHANNEL_CONFIG, Consts.AUDIO_FORMAT);
        File pcmFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "test.pcm");
        File wavFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "test.wav");
//                File jsonFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "test.json");
        if (pcmFile.exists()) {
            wavFile.delete();
        }
        pcmToWavUtil.pcmToWav(pcmFile.getAbsolutePath(), wavFile.getAbsolutePath());
        startPython(wavFile.getAbsolutePath());
    }

    // 初始化Python环境
    void initPython() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
    }
    public void startPython(String wavFile) {
        new Thread(new Runnable() {
            public void run() {
                // a potentially time consuming task
                Python py = Python.getInstance();
                PyObject obj1 = py.getModule("WAV_TO_MFCC").callAttr("wav_to_mfcc", wavFile);
//                PyObject obj1 = py.getModule("Test").callAttr("test");
//                String result2 = obj1.toJava(String.class);
                mfcc = obj1.toJava(float[][][][].class);

                json.post(new Runnable() {
                    public void run() {
//                        result.setText(Arrays.deepToString(result4[0][0]));
//                        result.setText(Float.toString(result3[0][0][0][0]));
                        if (mfcc.length > 0){
//                            mJson.setEnabled(true);
//                            hud.dismiss();
//                            json.setText(getString(R.string.start_det));
                            float total = 0;
                            for (int i=0; i<mfcc.length; i++){
                                float[][][][] result4 = extendFloat(i, mfcc);
                                float result2 = mTFLite.run(result4);
                                total += result2;
//                                total = total + result2;
                            }
                            hud.dismiss();
                            DecimalFormat dF = new DecimalFormat("0.00");
                            String result5 = dF.format(total/mfcc.length);
                            showResult(getString(R.string.result), result5);
                        }else {
                            json.setText("Too short.");
                        }
                    }
                });
            }
        }).start();
    }

    private float[][][][] extendFloat(int x, float[][][][] src) {
        float[][][][] des = new float[1][282][32][1];
        for (int i=0; i < 13; i++){
            for (int j=0; j<282; j++){
                des[0][j][i+9][0] = src[x][j][i][0];
            }
        }
        return des;
    }


    private void initTensorFlowAndLoadModel() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mTFLite == null)
                        mTFLite = new KerasTFLite(MainActivity.this);

                } catch (final Exception e) {
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }

    @Override
    protected void onPause() {

        Log.i(TAG, "onPause");
        super.onPause();
    }
    @Override
    protected void onStop() {
        if (mTFLite != null) {
            mTFLite.release();
            mTFLite = null;
        }
        Log.i(TAG, "onStop");
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }

    //倒计时60秒,这里不直接写60000,而用1000*60是因为后者看起来更直观,每走一步是1000毫秒也就是1秒
    CountDownTimer timer = new CountDownTimer(1000 * 3, 1000) {
        @SuppressLint("DefaultLocale")
        @Override
        public void onTick(long millisUntilFinished) {
            mAudioControl.setEnabled(false);
            mAudioControl.setText(String.format("(%d)",millisUntilFinished/1000));
        }

        @Override
        public void onFinish() {
            mAudioControl.setEnabled(true);
            mAudioControl.setText(getString(R.string.stop_record));
        }
    };

    /**
     * Show the messages under "detect" button.
     * @param msg
     * @param prob
     */
    private void showResult(String msg, String prob){
        json.setText(msg);
        result.setText(prob);
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_control:
                mPermissionList.clear();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    for (int i = 0; i < permissions.length; i++) {
                        if (ContextCompat.checkSelfPermission(getApplicationContext(), permissions[i])
                                != PackageManager.PERMISSION_GRANTED) {
                            mPermissionList.add(permissions[i]);
                        }
                    }
                    if (!mPermissionList.isEmpty()) {
                        String[] permissions = mPermissionList.toArray(new String[mPermissionList.size()]);
                        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION);
                    } else {
                        if (mAudioControl.getText().toString().endsWith(getString(R.string.start_record))) {
                            timer.start();
                            mAudioControl.setText(getString(R.string.stop_record));
                            startRecord();

                            mJson.setEnabled(false);
                            showResult(getString(R.string.recording), " ");
                        } else {
                            mAudioControl.setText(getString(R.string.start_record));
                            stopRecord();

                            mJson.setEnabled(true);
                            showResult(getString(R.string.start_det), " ");

                        }
                    }

                }

                break;

            case R.id.btn_json:
                pcmToWav();

                hud = KProgressHUD.create(this)
                        .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                        .setLabel(getString(R.string.hud_wait))
                        .setDetailsLabel(getString(R.string.hud_prc))
                        .setBackgroundColor(ContextCompat.getColor(this, R.color.teal_200))
                        .show();
                showResult(" ", " ");
//                json.setText(getString(R.string.hud_prc));

//                float total = 0;
//                for (int i=0; i<mfcc.length; i++){
//                    float[][][][] result4 = extendFloat(i, mfcc);
//                    float result2 = mTFLite.run(result4);
//                    total = total + result2;
//                }
//                DecimalFormat dF = new DecimalFormat("0.00");
//                String result5 = dF.format(total/mfcc.length);
//                json.setText(getString(R.string.result));
//                result.setText(result5);
                break;
        }
    }

}