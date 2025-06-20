package com.baidu.paddle.lite.demo.ppocr_demo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.paddle.lite.demo.common.Utils;
import com.baidu.paddle.lite.demo.common.camera2usingopengl.CameraSurfaceView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends Activity implements CameraSurfaceView.OnTextureChangedListener {
    CameraSurfaceView svPreview;
    TextView tvStatus;
    ImageButton btnSwitch;
    ImageButton btnShutter;

    String savedImagePath = "images/save.jpg";
    int lastFrameIndex = 0;
    long lastFrameTime;

    // Model settings of object detection
    protected String detModelPath = "ch_ppocr_mobile_v2.0_det_slim_opt.nb";
    protected String recModelPath = "ch_ppocr_mobile_v2.0_rec_slim_opt.nb";
    protected String clsModelPath = "ch_ppocr_mobile_v2.0_cls_slim_opt.nb";
    protected String labelPath = "ppocr_keys_v1.txt";
    protected String configPath = "config.txt";
    protected int cpuThreadNum = 1;
    protected String cpuPowerMode = "LITE_POWER_HIGH";


    Native predictor = new Native();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);


        // Init the camera preview and UI components
        initView();

        // Check and request CAMERA and WRITE_EXTERNAL_STORAGE permissions
        if (!checkAllPermissions()) {
            requestAllPermissions();
        }
    }

    @Override
    public RecTextResult onTextureChanged(int inTextureId, int outTextureId, int textureWidth, int textureHeight) {
        String savedImagePath = "";
        synchronized (this) {
            savedImagePath = MainActivity.this.savedImagePath;
        }
        savedImagePath = Utils.getDCIMDirectory() + File.separator + "result.jpg";
        RecTextResult modified = predictor.process(inTextureId, outTextureId, textureWidth, textureHeight, savedImagePath);
        if (!savedImagePath.isEmpty()) {
            synchronized (this) {
                MainActivity.this.savedImagePath = "";
            }
        }
        lastFrameIndex++;
        if (lastFrameIndex >= 30) {
            final int fps = (int) (lastFrameIndex * 1e9 / (System.nanoTime() - lastFrameTime));
            runOnUiThread(() -> {
                final String formattedFps = fps + "fps";
                tvStatus.setText(formattedFps);
            });
            lastFrameIndex = 0;
            lastFrameTime = System.nanoTime();
        }
        return modified;
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkRun();
        /*// Reload settings and re-initialize the predictor
        // Open camera until the permissions have been granted
        if (!checkAllPermissions()) {
            svPreview.disableCamera();
        }
        svPreview.onResume();*/
        svPreview.onResume();
    }

    @Override
    protected void onPause() {
        svPreview.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (predictor != null) {
            predictor.release();
        }
        super.onDestroy();
    }

    public void initView() {
        svPreview = findViewById(R.id.sv_preview);

        // Set rectangle bounds (30% left, 20% top, 70% right, 80% bottom)
        //svPreview.setRectangleBounds(0.3f, 0.2f, 0.7f, 0.8f);
        // Optional: Change rectangle color
        //svPreview.setRectangleColor(Color.RED);  // Change to red

        svPreview.setOnTextureChangedListener(this);
        tvStatus = findViewById(R.id.tv_status);
        btnSwitch = findViewById(R.id.btn_switch);
        btnSwitch.setOnClickListener(v -> svPreview.switchCamera());
        btnShutter = findViewById(R.id.btn_shutter);
        btnShutter.setOnClickListener(v -> {
            SimpleDateFormat date = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault());
            synchronized (this) {
                savedImagePath = Utils.getDCIMDirectory() + File.separator + date.format(new Date()) + ".png";
            }
            Toast.makeText(MainActivity.this, "Save snapshot to " + savedImagePath, Toast.LENGTH_SHORT).show();
        });
    }

    public void checkRun() {
            try {
            Utils.copyAssets(this, labelPath);
            String labelRealDir = new File(
                    this.getExternalFilesDir(null),
                    labelPath).getAbsolutePath();

            Utils.copyAssets(this, configPath);
            String configRealDir = new File(
                    this.getExternalFilesDir(null),
                    configPath).getAbsolutePath();

            Utils.copyAssets(this, detModelPath);
            String detRealModelDir = new File(
                    this.getExternalFilesDir(null),
                    detModelPath).getAbsolutePath();

            Utils.copyAssets(this, clsModelPath);
            String clsRealModelDir = new File(
                    this.getExternalFilesDir(null),
                    clsModelPath).getAbsolutePath();

            Utils.copyAssets(this, recModelPath);
            String recRealModelDir = new File(
                    this.getExternalFilesDir(null),
                    recModelPath).getAbsolutePath();

                predictor.init(
                        this,
                        detRealModelDir,
                        clsRealModelDir,
                        recRealModelDir,
                        configRealDir,
                        labelRealDir,
                        cpuThreadNum,
                        cpuPowerMode);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Permission denied")
                    .setMessage("Click to force quit the app, then open Settings->Apps & notifications->Target " +
                            "App->Permissions to grant all of the permissions.")
                    .setCancelable(false)
                    .setPositiveButton("Exit", (dialog, which) -> MainActivity.this.finish()).show();
        }
    }

    private void requestAllPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA}, 0);
    }

    private boolean checkAllPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
}
