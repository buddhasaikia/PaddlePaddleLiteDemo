package com.baidu.paddle.lite.demo.common.camera2usingopengl;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;

import com.baidu.paddle.lite.demo.common.Utils;
import com.baidu.paddle.lite.demo.ppocr_demo.RecTextResult;
import com.baidu.paddle.lite.demo.ppocr_demo.RecTextResultProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraSurfaceView extends GLSurfaceView
    implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    
    private static final String TAG = "CameraSurfaceView";
    private final Context context;
    private SurfaceTexture surfaceTexture;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;



    public static final int EXPECTED_PREVIEW_WIDTH = 1280;
    public static final int EXPECTED_PREVIEW_HEIGHT = 720;

    private float rectLeft = 0.2f;
    private float rectRight = 0.8f;
    private float rectTop = 0.2f;
    private float rectBottom = 0.8f;
    private int rectangleColor = Color.GREEN;

    protected int numberOfCameras;
    protected int selectedCameraId;
    protected boolean disableCamera = false;

    protected int surfaceWidth = 0;
    protected int surfaceHeight = 0;
    protected int textureWidth = 0;
    protected int textureHeight = 0;

    // In order to manipulate the camera preview data and render the modified one
    // to the screen, three textures are created and the data flow is shown as following:
    // previewdata->camTextureId->fboTexureId->drawTexureId->framebuffer
    protected int[] fbo = {0};
    protected int[] camTextureId = {0};
    protected int[] fboTextureId = {0};
    protected int[] drawTextureId = {0};


    private final float[] vertexCoordinates = {
            -1, -1,
            -1, 1,
            1, -1,
            1, 1};
    private final float[] textureCoordinates = {
            0, 1,
            0, 0,
            1, 1,
            1, 0};

    private FloatBuffer vertexCoordinatesBuffer;
    private FloatBuffer textureCoordinatesBuffer;
    private FloatBuffer rectangleVerticesBuffer;

    private int progCam2FBO = -1;
    private int progTex2Screen = -1;
    private int progRectangle = -1;
    private int vcCam2FBO;
    private int tcCam2FBO;
    private int vcTex2Screen;
    private int tcTex2Screen;
    private int vcRectangle;
    private int colorUniformLocation;
    private int rectUniformLocation;
    private int scanCount = 0;


    public interface OnTextureChangedListener {
        RecTextResult onTextureChanged(int inTextureId, int outTextureId, int textureWidth, int textureHeight);
    }

    private OnTextureChangedListener onTextureChangedListener = null;

    public void setOnTextureChangedListener(OnTextureChangedListener listener) {
        onTextureChangedListener = listener;
    }

    // Constructor required for use in XML layouts
    public CameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    private void init() {
        // Set up OpenGL ES 2.0 context
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        
        // Initialize camera manager
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        startBackgroundThread();
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // Create FBO and related textures
        GLES20.glGenFramebuffers(1, fbo, 0);
        GLES20.glGenTextures(1, fboTextureId, 0);
        GLES20.glGenTextures(1, drawTextureId, 0);

        // Initialize FBO texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        // Initialize draw texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, drawTextureId[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        // Create OES texture for storing camera preview data(YUV format)
        GLES20.glGenTextures(1, camTextureId, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, camTextureId[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        surfaceTexture = new SurfaceTexture(camTextureId[0]);
        surfaceTexture.setOnFrameAvailableListener(this);

        // Prepare vertex and texture coordinates
        int bytes = vertexCoordinates.length * Float.SIZE / Byte.SIZE;
        vertexCoordinatesBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer();
        textureCoordinatesBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexCoordinatesBuffer.put(vertexCoordinates).position(0);
        textureCoordinatesBuffer.put(textureCoordinates).position(0);

        // Create vertex and fragment shaders
        // camTextureId->fboTexureId
        String vss = "attribute vec2 vPosition;\n"
                + "attribute vec2 vTexCoord;\n"
                + "varying vec2 texCoord;\n"
                + "void main() {\n"
                + "  texCoord = vTexCoord;\n"
                + "  gl_Position = vec4 (vPosition.x, vPosition.y, 0.0, 1.0);\n"
                + "}";

        // Add rectangle bounds
        String fssCam2FBO = "#extension GL_OES_EGL_image_external : require\n"
                + "precision mediump float;\n"
                + "uniform samplerExternalOES sTexture;\n"
                + "varying vec2 texCoord;\n"
                + "uniform vec4 uRect;\n"  // Add rectangle bounds
                + "void main() {\n"
                + "  if (texCoord.x >= uRect.x && texCoord.x <= uRect.y && \n"
                + "      texCoord.y >= uRect.z && texCoord.y <= uRect.w) {\n"
                + "    gl_FragColor = texture2D(sTexture,texCoord);\n"
                + "  } else {\n"
                + "    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);\n"
                + "  }\n"
                + "}";
        progCam2FBO = Utils.createShaderProgram(vss, fssCam2FBO);
        vcCam2FBO = GLES20.glGetAttribLocation(progCam2FBO, "vPosition");
        tcCam2FBO = GLES20.glGetAttribLocation(progCam2FBO, "vTexCoord");
        rectUniformLocation = GLES20.glGetUniformLocation(progCam2FBO, "uRect");
        GLES20.glEnableVertexAttribArray(vcCam2FBO);
        GLES20.glEnableVertexAttribArray(tcCam2FBO);
        // fboTexureId/drawTexureId -> screen
        String fssTex2Screen = "precision mediump float;\n"
                + "uniform sampler2D sTexture;\n"
                + "varying vec2 texCoord;\n"
                + "void main() {\n"
                + "  gl_FragColor = texture2D(sTexture,texCoord);\n"
                + "}";
        progTex2Screen = Utils.createShaderProgram(vss, fssTex2Screen);
        vcTex2Screen = GLES20.glGetAttribLocation(progTex2Screen, "vPosition");
        tcTex2Screen = GLES20.glGetAttribLocation(progTex2Screen, "vTexCoord");

        // Setup rectangle program
        // New shader for rectangle overlay
        String vssOverlay = "attribute vec2 vPosition;\n"
                + "void main() {\n"
                + "  gl_Position = vec4(vPosition.x, vPosition.y, 0.0, 1.0);\n"
                + "}";
        String fssOverlay = "precision mediump float;\n"
                + "uniform vec4 uColor;\n"
                + "void main() {\n"
                + "  gl_FragColor = uColor;\n"
                + "}";
        progRectangle = Utils.createShaderProgram(vssOverlay, fssOverlay);
        vcRectangle = GLES20.glGetAttribLocation(progRectangle, "vPosition");
        colorUniformLocation = GLES20.glGetUniformLocation(progRectangle, "uColor");

        // Prepare rectangle vertices buffer
        float[] rectangleVertices = {
                rectLeft * 2 - 1, rectTop * 2 - 1,
                rectLeft * 2 - 1, rectBottom * 2 - 1,
                rectRight * 2 - 1, rectTop * 2 - 1,
                rectRight * 2 - 1, rectBottom * 2 - 1,
                rectLeft * 2 - 1, rectTop * 2 - 1,
                rectRight * 2 - 1, rectTop * 2 - 1,
                rectLeft * 2 - 1, rectBottom * 2 - 1,
                rectRight * 2 - 1, rectBottom * 2 - 1
        };

        rectangleVerticesBuffer = ByteBuffer.allocateDirect(rectangleVertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        rectangleVerticesBuffer.put(rectangleVertices).position(0);

        GLES20.glEnableVertexAttribArray(vcCam2FBO);
        GLES20.glEnableVertexAttribArray(tcCam2FBO);
        GLES20.glEnableVertexAttribArray(vcTex2Screen);
        GLES20.glEnableVertexAttribArray(tcTex2Screen);
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        //GLES20.glViewport(0, 0, width, height);

        surfaceWidth = width;
        surfaceHeight = height;

        // Set texture dimensions
        textureWidth = EXPECTED_PREVIEW_WIDTH;
        textureHeight = EXPECTED_PREVIEW_HEIGHT;

        // Allocate texture storage
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, textureWidth, textureHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        // Setup FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTextureId[0], 0);

        // Also allocate storage for draw texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, drawTextureId[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, textureWidth, textureHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        openCamera();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        if (surfaceTexture == null) return;

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        surfaceTexture.updateTexImage();
        float[] matrix = new float[16];
        surfaceTexture.getTransformMatrix(matrix);

        // Draw to FBO with rectangle cropping
        // camTextureId->fboTexureId
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glViewport(0, 0, textureWidth, textureHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(progCam2FBO);

        // Set rectangle bounds
        GLES20.glUniform4f(rectUniformLocation, rectLeft, rectRight, rectTop, rectBottom);

        GLES20.glVertexAttribPointer(vcCam2FBO, 2, GLES20.GL_FLOAT, false, 4 * 2, vertexCoordinatesBuffer);
        textureCoordinatesBuffer.clear();
        textureCoordinatesBuffer.put(transformTextureCoordinates(textureCoordinates, matrix));
        textureCoordinatesBuffer.position(0);
        GLES20.glVertexAttribPointer(tcCam2FBO, 2, GLES20.GL_FLOAT, false, 4 * 2, textureCoordinatesBuffer);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, camTextureId[0]);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(progCam2FBO, "sTexture"), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFlush();

        // Check if the draw texture is set
        int targetTexureId = fboTextureId[0];
        if (onTextureChangedListener != null) {
            RecTextResult recTextResult = onTextureChangedListener.onTextureChanged(fboTextureId[0], drawTextureId[0], textureWidth, textureHeight);
            if (recTextResult.getRecText() != null && recTextResult.getRecTextScore() != null) {
                targetTexureId = drawTextureId[0];
                RecTextResultProcessor.Builder builder = new RecTextResultProcessor.Builder();
                Map<String, Float> processedResult = builder.setRecTextResult(recTextResult)
                        .process(0.9f)
                        .getResultAsMap();
                if (!processedResult.isEmpty()) {
                    Log.d("RecTextResultProcessor", "====================Scan #"+(scanCount++)+"=============================");
                    Set<Map.Entry<String, Float>> entrySet = processedResult.entrySet();
                    for (Map.Entry<String, Float> entry : entrySet) {
                        Log.d("RecTextResultProcessor", entry.getKey() +" = "+entry.getValue());
                    }
                }
            }
        }

        // fboTexureId/drawTexureId->Screen
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(progTex2Screen);
        GLES20.glVertexAttribPointer(vcTex2Screen, 2, GLES20.GL_FLOAT, false, 4 * 2, vertexCoordinatesBuffer);
        textureCoordinatesBuffer.clear();
        textureCoordinatesBuffer.put(textureCoordinates);
        textureCoordinatesBuffer.position(0);
        GLES20.glVertexAttribPointer(tcTex2Screen, 2, GLES20.GL_FLOAT, false, 4 * 2, textureCoordinatesBuffer);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, targetTexureId);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(progTex2Screen, "sTexture"), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFlush();

        // Draw rectangle overlay
        GLES20.glUseProgram(progRectangle);
        GLES20.glLineWidth(5.0f);

        float[] color = {
                Color.red(rectangleColor) / 255.0f,
                Color.green(rectangleColor) / 255.0f,
                Color.blue(rectangleColor) / 255.0f,
                Color.alpha(rectangleColor) / 255.0f
        };

        GLES20.glUniform4fv(colorUniformLocation, 1, color, 0);
        GLES20.glVertexAttribPointer(vcRectangle, 2, GLES20.GL_FLOAT, false, 0, rectangleVerticesBuffer);
        GLES20.glEnableVertexAttribArray(vcRectangle);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 8);
    }

    private float[] transformTextureCoordinates(float[] coords, float[] matrix) {
        float[] result = new float[coords.length];
        float[] vt = new float[4];
        for (int i = 0; i < coords.length; i += 2) {
            float[] v = {coords[i], coords[i + 1], 0, 1};
            Matrix.multiplyMV(vt, 0, matrix, 0, v, 0);
            result[i] = vt[0];
            result[i + 1] = vt[1];
        }
        return result;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        requestRender();
    }

    private void openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            
            // Get the first back-facing camera
            String cameraId = null;
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
            
            if (cameraId != null) {
                cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to open camera", e);
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private void createCameraPreviewSession() {
        try {
            surfaceTexture.setDefaultBufferSize(textureWidth, textureHeight);

            Surface surface = new Surface(surfaceTexture);

            CaptureRequest.Builder previewRequestBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        if (cameraDevice == null) return;

                        captureSession = session;
                        try {
                            captureSession.setRepeatingRequest(
                                previewRequestBuilder.build(),
                                null,
                                backgroundHandler
                            );
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Failed to start camera preview", e);
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e(TAG, "Failed to configure camera session");
                    }
                },
                backgroundHandler
            );
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create camera preview session", e);
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop background thread", e);
            }
        }
    }

    // New methods for rectangle control
    public void setRectangleBounds(float left, float top, float right, float bottom) {
        rectLeft = left;
        rectRight = right;
        rectTop = top;
        rectBottom = bottom;

        float[] rectangleVertices = {
                rectLeft * 2 - 1, rectTop * 2 - 1,
                rectLeft * 2 - 1, rectBottom * 2 - 1,
                rectRight * 2 - 1, rectTop * 2 - 1,
                rectRight * 2 - 1, rectBottom * 2 - 1,
                rectLeft * 2 - 1, rectTop * 2 - 1,
                rectRight * 2 - 1, rectTop * 2 - 1,
                rectLeft * 2 - 1, rectBottom * 2 - 1,
                rectRight * 2 - 1, rectBottom * 2 - 1
        };

        rectangleVerticesBuffer.clear();
        rectangleVerticesBuffer.put(rectangleVertices).position(0);
        requestRender();
    }

    public void setRectangleColor(int color) {
        rectangleColor = color;
        requestRender();
    }

    public RectF getRectangleBounds() {
        return new RectF(rectLeft, rectTop, rectRight, rectBottom);
    }

    public void disableCamera() {
        disableCamera = true;
    }

    public void switchCamera() {
        closeCamera();
        selectedCameraId = (selectedCameraId + 1) % numberOfCameras;
        openCamera();
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    public void onResume() {
        startBackgroundThread();
    }

    public void onPause() {
        closeCamera();
        stopBackgroundThread();
    }
}