package com.example.night_fury.eagle;

import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.example.night_fury.eagle.VideoUtils;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    public static final int CONTROL_CODE = 1;

    private SurfaceView sfv_preview;
    private Camera camera = null;
    private ImageView IV_startRecord;
    private ImageView IV_pauseRecord;
    private Chronometer chronometer;
    private long recordCurrentTime = 0;

    private boolean isRecording;
    private boolean isPause;

    private MediaRecorder mediaRecorder;
    private String currentFileName;
    private String saveFileName;
    private String saveFilePath;
    private File vRecordFile;


    private Handler mHandler = new MyHandler(this);
    private static class MyHandler extends Handler{
        private final WeakReference<MainActivity> mActivity;

        public  MyHandler(MainActivity activity){
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg){
            System.out.println(msg);
            if(mActivity.get() == null){
                return;
            }
            switch (msg.what){
                case CONTROL_CODE:
                    mActivity.get().IV_startRecord.setEnabled(true);
                    break;
            }
        }
    }



    private SurfaceHolder.Callback cpHolderCallBack = new SurfaceHolder.Callback(){
        @Override
        public void surfaceCreated(SurfaceHolder holder){
            initCamera();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height){

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder){
            stopCamera();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
    }

    private void initViews(){
        sfv_preview = findViewById(R.id.sfv_preview);
        IV_startRecord = findViewById(R.id.record_control);
        IV_pauseRecord = findViewById(R.id.record_pause);
        chronometer = findViewById(R.id.record_time);

        sfv_preview.getHolder().addCallback(cpHolderCallBack);
        sfv_preview.getHolder().setFixedSize(640, 480);
        sfv_preview.setKeepScreenOn(true);

        IV_startRecord.setOnClickListener(this);
        IV_pauseRecord.setOnClickListener(this);
        IV_pauseRecord.setEnabled(false);

        chronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener(){
            @Override
            public void onChronometerTick(Chronometer cnm){
                recordCurrentTime += 1;
            }
        });
    }



    private void initCamera(){
        if(camera != null){
            stopCamera();
        }
        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        if(camera == null){
            Toast.makeText(this, "未能获取相机!", Toast.LENGTH_SHORT).show();
            return;
        }
        try{
            camera.setPreviewDisplay(sfv_preview.getHolder());
            setCameraParams();
            camera.startPreview();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    private void setCameraParams(){
        if(camera != null){
            Camera.Parameters params = camera.getParameters();
            if(this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE){
                params.set("orientation", "portrait");
                camera.setDisplayOrientation(90);
            }else {
                params.set("orientation", "landscape");
                camera.setDisplayOrientation(0);
            }
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            params.setRecordingHint(true);
            if(params.isVideoStabilizationSupported())
                params.setVideoStabilization(true);
            camera.setParameters(params);
        }
    }

    private void stopCamera(){
        if(camera != null){
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    public void startRecord(){
        boolean createOK = createRecordFile();
        if(!createOK){
            return;
        }
        initCamera();
        camera.unlock();
        configRecord();
        try{
            mediaRecorder.prepare();
            mediaRecorder.start();
        }catch (IOException e){
            e.printStackTrace();
        }
        if(recordCurrentTime != 0){
            chronometer.setBase(SystemClock.elapsedRealtime() - recordCurrentTime*1000);
        }else {
            chronometer.setBase(SystemClock.elapsedRealtime());
        }
        chronometer.start();
    }

    public void stopRecord(){
        if(mediaRecorder != null){
            mediaRecorder.setOnErrorListener(null);
            mediaRecorder.setPreviewDisplay(null);
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
            chronometer.stop();
            IV_startRecord.setEnabled(true);
            camera.lock();
        }
    }

    private boolean createRecordFile(){
        if(!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())){
            Toast.makeText(this, "请检查您的SD卡是否存在!", Toast.LENGTH_SHORT).show();
            return false;
        }
        File record_folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Record");
        if(!record_folder.exists()){
            record_folder.mkdirs();
        }
        String file_name = "VID" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4";
        if(recordCurrentTime == 0){
            saveFilePath = record_folder.getAbsolutePath() + "//";
            saveFileName = record_folder.getAbsolutePath() + "//" + file_name;
        }
        currentFileName = record_folder.getAbsolutePath() + "//" + file_name;
        vRecordFile = new File(record_folder, file_name);
        return true;
    }

    private void configRecord(){
        mediaRecorder = new MediaRecorder();
        mediaRecorder.reset();
        mediaRecorder.setCamera(camera);
        mediaRecorder.setOnErrorListener(OnErrorListener);

        mediaRecorder.setPreviewDisplay(sfv_preview.getHolder().getSurface());
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.VideoEncoder.DEFAULT);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioChannels(2);
        mediaRecorder.setMaxDuration(10*1024*1024);

        CamcorderProfile mProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        mediaRecorder.setAudioEncodingBitRate(44100);
        if(mProfile.videoBitRate > 2*1024*1024){
            mediaRecorder.setVideoEncodingBitRate(2*1024*1024);
        }else {
            mediaRecorder.setVideoEncodingBitRate(1024*1024);
        }
        mediaRecorder.setVideoFrameRate(mProfile.videoFrameRate);

        mediaRecorder.setOrientationHint(90);
        mediaRecorder.setVideoSize(640,480);
        mediaRecorder.setOutputFile(vRecordFile.getAbsolutePath());
    }


//    private String saveFile(byte[] bytes){
//        try{
//            File file = File.createTempFile("img", "");
//            FileOutputStream fos = new FileOutputStream(file);
//            fos.write(bytes);
//            fos.flush();
//            fos.close();
//            return file.getAbsolutePath();
//        }catch (IOException e){
//            e.printStackTrace();
//            return null;
//        }
//    }
//
//    private void acceptServer(final byte[] data) throws IOException{
//        new Thread(){
//            @Override
//            public void run(){
//                try{
//                    Socket socket = new Socket("202.38.214.230", 12345);
//                    OutputStream os = socket.getOutputStream();
//                    PrintWriter pw = new PrintWriter(os);
//                    InetAddress address = InetAddress.getLocalHost();
//                    String ip = address.getHostAddress();
//
//                    pw.write(getChars(data));
//                    pw.flush();
//                    socket.shutdownOutput();
//                    socket.close();
//                }catch (IOException e){
//                    e.printStackTrace();
//                }
//            }
//        }.start();
//    }
//
//    public static char[] getChars(byte[] bytes){
//        Charset cs = Charset.forName("UTF-8");
//        ByteBuffer bb = ByteBuffer.allocate(bytes.length);
//        bb.put(bytes);
//        bb.flip();
//        CharBuffer cb = cs.decode(bb);
//        return cb.array();
//    }
    private MediaRecorder.OnErrorListener OnErrorListener = new MediaRecorder.OnErrorListener() {
    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        try{
            if(mediaRecorder != null){
                mediaRecorder.reset();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
};

    @Override
    public void onClick(View view){
        switch (view.getId()){
            case R.id.record_control:
                if(!isRecording){
                //start record
                    startRecord();
                    isRecording = true;
                    IV_startRecord.setImageResource(R.drawable.recordvideo_stop);
                    IV_startRecord.setEnabled(false);//enable after 1s
                    mHandler.sendEmptyMessageDelayed(CONTROL_CODE, 1000);
                    IV_pauseRecord.setImageResource(R.drawable.control_pause);
                    IV_pauseRecord.setVisibility(View.VISIBLE);
                    IV_pauseRecord.setEnabled(true);
                }else{
                //stop record
                    IV_startRecord.setImageResource(R.drawable.record_video_start);
                    IV_pauseRecord.setVisibility(View.GONE);
                    IV_pauseRecord.setEnabled(false);
                    stopRecord();
//                    stopCamera();
                    isRecording = false;
                    recordCurrentTime = 0;
                    isPause = false;
                    if(!saveFileName.equals(currentFileName)){
//                        new Thread(new Runnable() {
//                            @Override
//                            public void run() {
                                try{
                                    String[] str = new String[]{saveFileName, currentFileName};
                                    VideoUtils.appendVideo(MainActivity.this, saveFilePath + "append.mp4", str);
                                    File oldFile = new File(saveFileName);
                                    File newFile = new File(saveFilePath + "append.mp4");
                                    newFile.renameTo(oldFile);
                                    if(oldFile.exists()){
                                        newFile.delete();
                                        new File(currentFileName).delete();
                                        currentFileName = saveFileName;
                                    }
                                }catch (IOException e){
                                    e.printStackTrace();
                                }
//                            }
//                        }).start();
                    }
                }
                break;

            case R.id.record_pause:
                if(!isPause){
                //pause when is recording
                    IV_pauseRecord.setImageResource(R.drawable.control_play);
                    //@// TODO: 18-3-31
                    camera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
                            if(success == true) {
                                MainActivity.this.camera.cancelAutoFocus();
                            }
                        }
                    });
                    stopRecord();
                    isPause = true;
                    if(!saveFileName.equals(currentFileName)){
//                        new Thread(new Runnable() {
//                            @Override
//                            public void run() {
                                try{
                                    String[] str = new String[]{saveFileName, currentFileName};
                                    VideoUtils.appendVideo(MainActivity.this, saveFilePath + "append.mp4", str);
                                    File oldFile = new File(saveFileName);
                                    File newFile = new File(saveFilePath + "append.mp4");
                                    newFile.renameTo(oldFile);
                                    if(oldFile.exists()){
                                        newFile.delete();
                                        new File(currentFileName).delete();
                                        currentFileName = saveFileName;
                                    }
                                }catch (IOException e){
                                    e.printStackTrace();
                                }
//                            }
//                        }).start();
                    }
                }else{
                //continue when is pause
                    IV_pauseRecord.setImageResource(R.drawable.control_pause);
                    startRecord();
                    isPause = false;
                }
                break;
        }
    }

}













