package com.baidu.paddle.lite.demo.ppocr_demo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.paddle.lite.demo.common.CameraSurfaceView;
import com.baidu.paddle.lite.demo.common.Utils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends Activity implements CameraSurfaceView.OnTextureChangedListener {
    CameraSurfaceView svPreview;
    TextView tvStatus;
    ImageButton btnSwitch;
    ImageButton btnShutter;
    ImageButton btnFilter;

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
    private int processingMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        // Init the camera preview and UI components
        initView();

        // Set Processing Mode after initializing svPreview
        svPreview.setProcessingMode(2); // Edge Detection

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
                String fpsStr = fps + "fps";
                tvStatus.setText(fpsStr);
            });
            lastFrameIndex = 0;
            lastFrameTime = System.nanoTime();
        }
        return modified;
    }

    public void initView() {
        svPreview = findViewById(R.id.svPreview);
        svPreview.setOnTextureChangedListener(this);
        tvStatus = findViewById(R.id.tvStatus);
        btnSwitch = findViewById(R.id.btnSwitch);
        btnSwitch.setOnClickListener(view -> svPreview.switchCamera());
        btnShutter = findViewById(R.id.btnShutter);
        btnFilter = findViewById(R.id.btnFilter);
        btnShutter.setOnClickListener(view -> {
            SimpleDateFormat date = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault());
            synchronized (this) {
                savedImagePath = Utils.getDCIMDirectory() + File.separator + date.format(new Date()) + ".png";
            }
            Toast.makeText(MainActivity.this, "Save snapshot to " + savedImagePath, Toast.LENGTH_SHORT).show();
        });

        btnFilter.setOnClickListener(v -> {
            processingMode = (processingMode + 1) % 5; // Cycle through 0-4 modes
            svPreview.setProcessingMode(processingMode);
            Toast.makeText(MainActivity.this, "Filter Mode: " + processingMode, Toast.LENGTH_SHORT).show();
        });

        svPreview.setProcessingMode(1); // Grayscale
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
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            MainActivity.this.finish();
                        }
                    }).show();
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

    @Override
    protected void onResume() {
        super.onResume();
        // Reload settings and re-initialize the predictor
        checkRun();
        // Open camera until the permissions have been granted
        if (!checkAllPermissions()) {
            svPreview.disableCamera();
        }
        svPreview.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        svPreview.onPause();
    }

    @Override
    protected void onDestroy() {
        if (predictor != null) {
            predictor.release();
        }
        super.onDestroy();
    }
}
