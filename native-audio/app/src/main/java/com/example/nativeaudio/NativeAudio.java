/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.nativeaudio;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
//import android.support.annotation.NonNull;
//import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.Toast;

public class NativeAudio extends Activity
        // implements ActivityCompat.OnRequestPermissionsResultCallback {
{
    static final String TAG = "NativeAudio";
    private static final int AUDIO_ECHO_REQUEST = 0;

    static final int CLIP_NONE = 0;
    static final int CLIP_HELLO = 1;
    static final int CLIP_ANDROID = 2;
    static final int CLIP_SAWTOOTH = 3;
    static final int CLIP_PLAYBACK = 4;

    static String URI;
    static AssetManager assetManager;

    static boolean isPlayingAsset = false;
    static boolean isPlayingUri = false;

    static int numChannelsUri = 0;

    /** Called when the activity is first created. */
    @Override
    @TargetApi(17)
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);

        Log.d(TAG, "onCreate ----------- ");

        assetManager = getAssets();


        /*
            Ctrl+O 重写方法
            Ctrl+I 实现接口

            Ctrl+N 查找方法/接口
            Ctrl+Shift+N 查找文件
            Ctrl+Shift+ALt+N 查找符号


            Ctrl+Space 代码补全提示
            Ctrl+Shift+Space 智能代码补全
            Ctrl+Shift+Enter 该行自动补全(光标跳到最后 加上分号; )
            Ctrl+P 函数签名

            Alt+Enter 自动修复/意图动作/自动补全

            Ctrl+J 代码模板 (创建 for foreach Toast system.out.println) 或者直接敲入logm logr loge等回车
          */

        // initialize native audio system
        createEngine();

        int sampleRate = 0;
        int bufSize = 0;
        /*
         * retrieve fast audio path sample rate and buf size; if we have it, we pass to native
         * side to create a player with fast audio enabled [ fast audio == low latency audio ];
         * IF we do not have a fast audio path, we pass 0 for sampleRate, which will force native
         * side to pick up the 8Khz sample rate.
         */
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            AudioManager myAudioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            String nativeParam = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            sampleRate = Integer.parseInt(nativeParam);
            nativeParam = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            bufSize = Integer.parseInt(nativeParam); // 返回的采样率和buffer大小是字符串

            PackageManager pm = getPackageManager();
            boolean claimsFeature = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY);

            // 高通小米5 OUTPUT_SAMPLE_RATE = 48000 OUTPUT_FRAMES_PER_BUFFER = 192 FEATURE_AUDIO_LOW_LATENCY = false
            // MTK MT6735  OUTPUT_SAMPLE_RATE = 44100 OUTPUT_FRAMES_PER_BUFFER = 1024 FEATURE_AUDIO_LOW_LATENCY = false
            Log.d(TAG,"OUTPUT_SAMPLE_RATE = " + sampleRate + " OUTPUT_FRAMES_PER_BUFFER = " + bufSize
                + " FEATURE_AUDIO_LOW_LATENCY = " + claimsFeature );
        }

        // 一开始就默认创建了 buffer queue AudioPlayer Demo
        createBufferQueueAudioPlayer(sampleRate, bufSize);

        // initialize URI spinner
        Spinner uriSpinner = (Spinner) findViewById(R.id.uri_spinner);
        ArrayAdapter<CharSequence> uriAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.local_uri_spinner_array,
                /* R.array.uri_spinner_array, */
                android.R.layout.simple_spinner_item);
        uriAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        uriSpinner.setAdapter(uriAdapter);
        uriSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                 URI = parent.getItemAtPosition(pos).toString();
                /* 原来是 两个在线的音乐文件
                  <string-array name="uri_spinner_array">
                        <item>http://upload.wikimedia.org/wikipedia/commons/6/6d/Banana.ogg</item>
                        <item>http://www.freesound.org/data/previews/18/18765_18799-lq.mp3</item>
                  </string-array>
                 */
            }

            public void onNothingSelected(AdapterView parent) {
                URI = null;
            }

        });

        // initialize button click handlers

        ((Button) findViewById(R.id.hello)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                // ignore the return value
                selectClip(CLIP_HELLO, 2);
            }
        });

        ((Button) findViewById(R.id.android)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                // ignore the return value
                selectClip(CLIP_ANDROID, 7);
            }
        });

        ((Button) findViewById(R.id.sawtooth)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                // ignore the return value
                selectClip(CLIP_SAWTOOTH, 1);
            }
        });

        ((Button) findViewById(R.id.reverb)).setOnClickListener(new OnClickListener() {
            boolean enabled = false;
            public void onClick(View view) {
                enabled = !enabled;
                if (!enableReverb(enabled)) {
                    enabled = !enabled;
                }
            }
        });


        //  使用AssertManager 播放APK中包含的mp3文件
        //
        ((Button) findViewById(R.id.embedded_soundtrack)).setOnClickListener(new OnClickListener() {
            boolean created = false;
            public void onClick(View view) {
                if (!created) {
                    created = createAssetAudioPlayer(assetManager, "withus.mp3");
                }
                if (created) {
                    // 跟URI AudioPlayer Demo 不一样
                    // URI AudioPlayer Demo 创建后 要按play pause播放
                    // Assert/fd AudioPlayer  Demo 创建后立刻播放
                    isPlayingAsset = !isPlayingAsset;
                    setPlayingAssetAudioPlayer(isPlayingAsset);
                }
            }
        });

        ((Button) findViewById(R.id.uri_soundtrack)).setOnClickListener(new OnClickListener() {
            boolean created = false;
            public void onClick(View view) {
                if (!created && URI != null) {
                    Log.d(TAG , " uri_soundtrack create URI Audio Player URI " + URI );
                    //URI = "file:///mnt/sdcard/xxx.3gp" ;
                    //URI = "file:///mnt/sdcard/Banana.ogg" ;
                    created = createUriAudioPlayer(URI);
                }
             }
        });

        ((Button) findViewById(R.id.pause_uri)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Log.d(TAG , " setPlayingUriAudioPlayer  pause URI " );
                setPlayingUriAudioPlayer(false);
             }
        });

        ((Button) findViewById(R.id.play_uri)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Log.d(TAG , " setPlayingUriAudioPlayer  play URI " );
                setPlayingUriAudioPlayer(true);
             }
        });

        ((Button) findViewById(R.id.loop_uri)).setOnClickListener(new OnClickListener() {
            boolean isLooping = false;
            public void onClick(View view) {
                isLooping = !isLooping;
                setLoopingUriAudioPlayer(isLooping);

             }
        });

        ((Button) findViewById(R.id.mute_left_uri)).setOnClickListener(new OnClickListener() {
            boolean muted = false;
            public void onClick(View view) {
                muted = !muted;
                setChannelMuteUriAudioPlayer(0, muted);
             }
        });

        ((Button) findViewById(R.id.mute_right_uri)).setOnClickListener(new OnClickListener() {
            boolean muted = false;
            public void onClick(View view) {
                muted = !muted;
                setChannelMuteUriAudioPlayer(1, muted);
             }
        });

        ((Button) findViewById(R.id.solo_left_uri)).setOnClickListener(new OnClickListener() {
            boolean soloed = false;
            public void onClick(View view) {
                soloed = !soloed;
                setChannelSoloUriAudioPlayer(0, soloed);
             }
        });

        ((Button) findViewById(R.id.solo_right_uri)).setOnClickListener(new OnClickListener() {
            boolean soloed = false;
            public void onClick(View view) {
                soloed = !soloed;
                setChannelSoloUriAudioPlayer(1, soloed);
             }
        });

        ((Button) findViewById(R.id.mute_uri)).setOnClickListener(new OnClickListener() {
            boolean muted = false;
            public void onClick(View view) {
                muted = !muted;
                setMuteUriAudioPlayer(muted);
             }
        });

        /*
        *  单声道的数据源 做 立体平移 Stereo Panning
        *  https://developer.android.com/ndk/guides/audio/opensl-prog-notes.html#panning
        *
        * */
        ((Button) findViewById(R.id.enable_stereo_position_uri)).setOnClickListener(
                new OnClickListener() {
            boolean enabled = false;
            public void onClick(View view) {
                enabled = !enabled;
                enableStereoPositionUriAudioPlayer(enabled);
             }
        });

        // 获取通道数目  如果只是创建了AudioPlayer 还不能获取通道数 需要SetPlayState之后才能获取
        ((Button) findViewById(R.id.channels_uri)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (numChannelsUri == 0) {
                    numChannelsUri = getNumChannelsUriAudioPlayer();
                }
                Toast.makeText(NativeAudio.this, "Channels: " + numChannelsUri,
                        Toast.LENGTH_SHORT).show();
             }
        });


        // 音量改变
        ((SeekBar) findViewById(R.id.volume_uri)).setOnSeekBarChangeListener(
                new OnSeekBarChangeListener() {
            int lastProgress = 100;
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (BuildConfig.DEBUG && !(progress >= 0 && progress <= 100)) {
                    throw new AssertionError();
                }
                lastProgress = progress;
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
                int attenuation = 100 - lastProgress;
                int millibel = attenuation * -50; // 100 * -50  ~  0
                setVolumeUriAudioPlayer(millibel);
            }
        });

        ((SeekBar) findViewById(R.id.pan_uri)).setOnSeekBarChangeListener(
                new OnSeekBarChangeListener() {
            int lastProgress = 100;
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (BuildConfig.DEBUG && !(progress >= 0 && progress <= 100)) {
                    throw new AssertionError();
                }               
                lastProgress = progress;
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 左右声道 相对大小
                // 进度条在中间的时候 左右一样  进度条在<50 左边大声点 >50 右边大声点
                int permille = (lastProgress - 50) * 20;
                setStereoPositionUriAudioPlayer(permille);
            }
        });

        ((SeekBar) findViewById(R.id.playback_rate_uri)).setOnSeekBarChangeListener(
            new OnSeekBarChangeListener() {
                int lastProgress = 100;
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (BuildConfig.DEBUG && !(progress >= 0 && progress <= 100)) {
                        throw new AssertionError();
                    }
                    lastProgress = progress;
                }
                public void onStartTrackingTouch(SeekBar seekBar) {
                }
                public void onStopTrackingTouch(SeekBar seekBar) {

                    /*
                            0 ----- 50 ---- 100
                          -1000 --- 0 ---- 1000
                     */
                    int permille = lastProgress * (1000 - -1000) / 100  + (-1000) ;
                    setPlaybackRateUriAudioPlayer(permille);
                }
        });



        ((Button) findViewById(R.id.record)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
//                int status = ActivityCompat.checkSelfPermission(NativeAudio.this,
//                        Manifest.permission.RECORD_AUDIO);
//                if (status != PackageManager.PERMISSION_GRANTED) {
//                    ActivityCompat.requestPermissions(
//                            NativeAudio.this,
//                            new String[]{Manifest.permission.RECORD_AUDIO},
//                            AUDIO_ECHO_REQUEST);
//                    return;
//                }
                recordAudio();
            }
        });

        ((Button) findViewById(R.id.playback)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                // ignore the return value
                selectClip(CLIP_PLAYBACK, 3);
            }
        });

    }

    // Single out recording for run-permission needs
    static boolean created = false;
    private void recordAudio() {
        if (!created) {
            created = createAudioRecorder();
        }
        if (created) {
            startRecording();
        }
    }

   /** Called when the activity is about to be destroyed. */
    @Override
    protected void onPause()
    {
        Log.d(TAG, "onPause");
        // turn off all audio
        selectClip(CLIP_NONE, 0);
        isPlayingAsset = false;
        setPlayingAssetAudioPlayer(false);
        isPlayingUri = false;
        setPlayingUriAudioPlayer(false);
        super.onPause();
    }

    /** Called when the activity is about to be destroyed. */
    @Override
    protected void onDestroy()
    {
        Log.d(TAG, "onDestroy");
        shutdown();
        super.onDestroy();
    }

//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        /*
//         * if any permission failed, the sample could not play
//         */
//        if (AUDIO_ECHO_REQUEST != requestCode) {
//            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//            return;
//        }
//
//        if (grantResults.length != 1  ||
//                grantResults[0] != PackageManager.PERMISSION_GRANTED) {
//            /*
//             * When user denied the permission, throw a Toast to prompt that RECORD_AUDIO
//             * is necessary; on UI, we display the current status as permission was denied so
//             * user know what is going on.
//             * This application go back to the original state: it behaves as if the button
//             * was not clicked. The assumption is that user will re-click the "start" button
//             * (to retry), or shutdown the app in normal way.
//             */
//            Toast.makeText(getApplicationContext(),
//                    getString(R.string.NeedRecordAudioPermission),
//                    Toast.LENGTH_SHORT)
//                    .show();
//            return;
//        }
//
//        // The callback runs on app's thread, so we are safe to resume the action
//        recordAudio();
//    }

    /** Native methods, implemented in jni folder */
    public static native void createEngine();
    public static native void createBufferQueueAudioPlayer(int sampleRate, int samplesPerBuf);
    public static native boolean createAssetAudioPlayer(AssetManager assetManager, String filename);
    // true == PLAYING, false == PAUSED
    public static native void setPlayingAssetAudioPlayer(boolean isPlaying);
    public static native boolean createUriAudioPlayer(String uri);
    public static native void setPlayingUriAudioPlayer(boolean isPlaying);
    public static native void setLoopingUriAudioPlayer(boolean isLooping);
    public static native void setChannelMuteUriAudioPlayer(int chan, boolean mute);
    public static native void setChannelSoloUriAudioPlayer(int chan, boolean solo);
    public static native int getNumChannelsUriAudioPlayer();
    public static native void setVolumeUriAudioPlayer(int millibel); // mB
    public static native void setMuteUriAudioPlayer(boolean mute);
    public static native void enableStereoPositionUriAudioPlayer(boolean enable);
    public static native void setStereoPositionUriAudioPlayer(int permille);
    public static native void setPlaybackRateUriAudioPlayer(int permille); // 千分之？
    public static native boolean selectClip(int which, int count);
    public static native boolean enableReverb(boolean enabled);
    public static native boolean createAudioRecorder();
    public static native void startRecording();
    public static native void shutdown();

    /** Load jni .so on initialization */
    static {
         System.loadLibrary("native-audio-jni");
    }

}
