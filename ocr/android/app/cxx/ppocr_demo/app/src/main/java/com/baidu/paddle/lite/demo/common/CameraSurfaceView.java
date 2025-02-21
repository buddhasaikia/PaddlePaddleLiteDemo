package com.baidu.paddle.lite.demo.common;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;

import com.baidu.paddle.lite.demo.ppocr_demo.RecTextResult;
import com.baidu.paddle.lite.demo.ppocr_demo.RecTextResultProcessor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraSurfaceView extends GLSurfaceView implements Renderer,
        SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = CameraSurfaceView.class.getSimpleName();

    public static final int EXPECTED_PREVIEW_WIDTH = 1280;
    public static final int EXPECTED_PREVIEW_HEIGHT = 720;

    // Zoom related fields
    private float currentZoom = 0f;
    private float maxZoom = 0f;
    private boolean isZoomSupported = false;

    protected int numberOfCameras;
    protected int selectedCameraId;
    protected boolean disableCamera = false;
    protected Camera camera;

    protected Context context;
    protected SurfaceTexture surfaceTexture;
    protected int surfaceWidth = 0;
    protected int surfaceHeight = 0;
    protected int textureWidth = 0;
    protected int textureHeight = 0;

    private int processingMode = 0; // Default: Normal (0 = Normal, 1 = Grayscale, 2 = Edge, 3 = Threshold, 4 = Blur)

    // In order to manipulate the camera preview data and render the modified one
    // to the screen, three textures are created and the data flow is shown as following:
    // previewdata->camTextureId->fboTexureId->drawTexureId->framebuffer
    protected int[] fbo = {0};
    protected int[] camTextureId = {0};
    protected int[] fboTexureId = {0};
    protected int[] drawTexureId = {0};

    private final String vss = ""
            + "attribute vec2 vPosition;\n"
            + "attribute vec2 vTexCoord;\n" + "varying vec2 texCoord;\n"
            + "void main() {\n" + "  texCoord = vTexCoord;\n"
            + "  gl_Position = vec4 (vPosition.x, vPosition.y, 0.0, 1.0);\n"
            + "}";

    private final String fssCam2FBO = ""
            + "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "uniform samplerExternalOES sTexture;\n"
            + "varying vec2 texCoord;\n"
            + "uniform int mode;\n"  // Mode selector

            // Edge Detection (Sobel)
            + "vec3 applySobel(samplerExternalOES tex, vec2 uv) {\n"
            + "    float offset = 1.0 / 512.0;\n"
            + "    vec3 sample0 = texture2D(tex, uv + vec2(-offset, -offset)).rgb;\n"
            + "    vec3 sample1 = texture2D(tex, uv + vec2( 0.0, -offset)).rgb;\n"
            + "    vec3 sample2 = texture2D(tex, uv + vec2( offset, -offset)).rgb;\n"
            + "    vec3 sample3 = texture2D(tex, uv + vec2(-offset,  0.0)).rgb;\n"
            + "    vec3 sample4 = texture2D(tex, uv).rgb;\n"
            + "    vec3 sample5 = texture2D(tex, uv + vec2( offset,  0.0)).rgb;\n"
            + "    vec3 sample6 = texture2D(tex, uv + vec2(-offset,  offset)).rgb;\n"
            + "    vec3 sample7 = texture2D(tex, uv + vec2( 0.0,  offset)).rgb;\n"
            + "    vec3 sample8 = texture2D(tex, uv + vec2( offset,  offset)).rgb;\n"
            + "    vec3 gx = -sample0 - 2.0 * sample3 - sample6 + sample2 + 2.0 * sample5 + sample8;\n"
            + "    vec3 gy = -sample0 - 2.0 * sample1 - sample2 + sample6 + 2.0 * sample7 + sample8;\n"
            + "    return sqrt(gx * gx + gy * gy);\n"
            + "}\n"

            // Grayscale
            + "vec3 applyGrayscale(vec3 color) {\n"
            + "    float gray = dot(color, vec3(0.299, 0.587, 0.114));\n"
            + "    return vec3(gray);\n"
            + "}\n"

            // Thresholding
            + "vec3 applyThreshold(vec3 color, float threshold) {\n"
            + "    float gray = dot(color, vec3(0.299, 0.587, 0.114));\n"
            + "    return gray > threshold ? vec3(1.0) : vec3(0.0);\n"
            + "}\n"

            // Gaussian Blur
            + "vec3 applyGaussianBlur(samplerExternalOES tex, vec2 uv) {\n"
            + "    float offset = 1.0 / 512.0;\n"
            + "    vec3 color = texture2D(tex, uv).rgb * 0.36;\n"
            + "    color += texture2D(tex, uv + vec2(offset, 0.0)).rgb * 0.12;\n"
            + "    color += texture2D(tex, uv + vec2(-offset, 0.0)).rgb * 0.12;\n"
            + "    color += texture2D(tex, uv + vec2(0.0, offset)).rgb * 0.12;\n"
            + "    color += texture2D(tex, uv + vec2(0.0, -offset)).rgb * 0.12;\n"
            + "    return color;\n"
            + "}\n"

            // Main Function
            + "void main() {\n"
            + "    vec4 color = texture2D(sTexture, texCoord);\n"
            + "    if (mode == 1) {\n"
            + "        gl_FragColor = vec4(applyGrayscale(color.rgb), color.a);\n"
            + "    } else if (mode == 2) {\n"
            + "        gl_FragColor = vec4(applySobel(sTexture, texCoord), color.a);\n"
            + "    } else if (mode == 3) {\n"
            + "        gl_FragColor = vec4(applyThreshold(color.rgb, 0.5), color.a);\n"
            + "    } else if (mode == 4) {\n"
            + "        gl_FragColor = vec4(applyGaussianBlur(sTexture, texCoord), color.a);\n"
            + "    } else {\n"
            + "        gl_FragColor = color;\n"
            + "    }\n"
            + "}";



    private final String fssTex2Screen = ""
            + "precision mediump float;\n"
            + "uniform sampler2D sTexture;\n"
            + "varying vec2 texCoord;\n"
            + "void main() {\n"
            + "  gl_FragColor = texture2D(sTexture,texCoord);\n" + "}";

    private final float vertexCoords[] = {
            -1, -1,
            -1, 1,
            1, -1,
            1, 1};
    private float textureCoords[] = {
            0, 1,
            0, 0,
            1, 1,
            1, 0};

    private FloatBuffer vertexCoordsBuffer;
    private FloatBuffer textureCoordsBuffer;

    private int progCam2FBO = -1;
    private int progTex2Screen = -1;
    private int vcCam2FBO;
    private int tcCam2FBO;
    private int vcTex2Screen;
    private int tcTex2Screen;
    private int scanCount = 0;

    public interface OnTextureChangedListener {
        public RecTextResult onTextureChanged(int inTextureId, int outTextureId, int textureWidth, int textureHeight);
    }

    private OnTextureChangedListener onTextureChangedListener = null;

    public void setOnTextureChangedListener(OnTextureChangedListener listener) {
        onTextureChangedListener = listener;
    }

    public CameraSurfaceView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        context = ctx;
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        // Find the total number of available cameras and the ID of the default camera
        numberOfCameras = Camera.getNumberOfCameras();
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                selectedCameraId = i;
            }
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
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
        int bytes = vertexCoords.length * Float.SIZE / Byte.SIZE;
        vertexCoordsBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer();
        textureCoordsBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexCoordsBuffer.put(vertexCoords).position(0);
        textureCoordsBuffer.put(textureCoords).position(0);

        // Create vertex and fragment shaders
        // camTextureId->fboTexureId
        progCam2FBO = Utils.createShaderProgram(vss, fssCam2FBO);
        vcCam2FBO = GLES20.glGetAttribLocation(progCam2FBO, "vPosition");
        tcCam2FBO = GLES20.glGetAttribLocation(progCam2FBO, "vTexCoord");
        GLES20.glEnableVertexAttribArray(vcCam2FBO);
        GLES20.glEnableVertexAttribArray(tcCam2FBO);
        // fboTexureId/drawTexureId -> screen
        progTex2Screen = Utils.createShaderProgram(vss, fssTex2Screen);
        vcTex2Screen = GLES20.glGetAttribLocation(progTex2Screen, "vPosition");
        tcTex2Screen = GLES20.glGetAttribLocation(progTex2Screen, "vTexCoord");
        GLES20.glEnableVertexAttribArray(vcTex2Screen);
        GLES20.glEnableVertexAttribArray(tcTex2Screen);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        openCamera();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (surfaceTexture == null) return;

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        surfaceTexture.updateTexImage();
        float[] matrix = new float[16];
        surfaceTexture.getTransformMatrix(matrix);

        // camTextureId->fboTexureId
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glViewport(0, 0, textureWidth, textureHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(progCam2FBO);
        int modeLocation = GLES20.glGetUniformLocation(progCam2FBO, "mode");
        GLES20.glUniform1i(modeLocation, processingMode);
        GLES20.glVertexAttribPointer(vcCam2FBO, 2, GLES20.GL_FLOAT, false, 4 * 2, vertexCoordsBuffer);
        textureCoordsBuffer.clear();
        textureCoordsBuffer.put(transformTextureCoordinates(textureCoords, matrix));
        textureCoordsBuffer.position(0);
        GLES20.glVertexAttribPointer(tcCam2FBO, 2, GLES20.GL_FLOAT, false, 4 * 2, textureCoordsBuffer);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, camTextureId[0]);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(progCam2FBO, "sTexture"), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFlush();

        // Check if the draw texture is set
        int targetTexureId = fboTexureId[0];
        if (onTextureChangedListener != null) {
            RecTextResult recTextResult = onTextureChangedListener.onTextureChanged(fboTexureId[0], drawTexureId[0],
                    textureWidth, textureHeight);
            if (recTextResult.getRecText() != null && recTextResult.getRecTextScore() != null) {
                targetTexureId = drawTexureId[0];
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
        GLES20.glVertexAttribPointer(vcTex2Screen, 2, GLES20.GL_FLOAT, false, 4 * 2, vertexCoordsBuffer);
        textureCoordsBuffer.clear();
        textureCoordsBuffer.put(textureCoords);
        textureCoordsBuffer.position(0);
        GLES20.glVertexAttribPointer(tcTex2Screen, 2, GLES20.GL_FLOAT, false, 4 * 2, textureCoordsBuffer);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, targetTexureId);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(progTex2Screen, "sTexture"), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFlush();
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
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        requestRender();
    }

    public void disableCamera() {
        disableCamera = true;
    }

    public void switchCamera() {
        releaseCamera();
        selectedCameraId = (selectedCameraId + 1) % numberOfCameras;
        openCamera();
    }
    public void setProcessingMode(int mode) {
        this.processingMode = mode;
        requestRender(); // Request a frame update with the new mode
    }


    public void openCamera() {
        if (disableCamera) return;
        camera = Camera.open(selectedCameraId);

        // Check for zoom support
        Camera.Parameters parameters = camera.getParameters();
        if (parameters.isZoomSupported()) {
            isZoomSupported = true;
            maxZoom = parameters.getMaxZoom();
            // Reset zoom to initial state
            currentZoom = 0f;
            updateCameraZoom();
        }

        List<Size> supportedPreviewSizes = camera.getParameters().getSupportedPreviewSizes();
        Size previewSize = Utils.getOptimalPreviewSize(supportedPreviewSizes, EXPECTED_PREVIEW_WIDTH,
                EXPECTED_PREVIEW_HEIGHT);
        parameters.setPreviewSize(previewSize.width, previewSize.height);
        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        camera.setParameters(parameters);
        int degree = Utils.getCameraDisplayOrientation(context, selectedCameraId);
        camera.setDisplayOrientation(degree);
        boolean rotate = degree == 90 || degree == 270;
        textureWidth = rotate ? previewSize.height : previewSize.width;
        textureHeight = rotate ? previewSize.width : previewSize.height;
        // Destroy FBO and draw textures
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glDeleteFramebuffers(1, fbo, 0);
        GLES20.glDeleteTextures(1, drawTexureId, 0);
        GLES20.glDeleteTextures(1, fboTexureId, 0);
        // Normal texture for storing modified camera preview data(RGBA format)
        GLES20.glGenTextures(1, drawTexureId, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, drawTexureId[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, textureWidth, textureHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        // FBO texture for storing camera preview data(RGBA format)
        GLES20.glGenTextures(1, fboTexureId, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexureId[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, textureWidth, textureHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        // Generate FBO and bind to FBO texture
        GLES20.glGenFramebuffers(1, fbo, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                fboTexureId[0], 0);
        try {
            camera.setPreviewTexture(surfaceTexture);
        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
        camera.startPreview();
    }

    // Add new methods for zoom control
    public void setZoom(float zoomLevel) {
        if (!isZoomSupported || camera == null) return;

        // Ensure zoom level is between 0 and 1
        currentZoom = Math.max(0f, Math.min(1f, zoomLevel));
        updateCameraZoom();
    }

    public float getCurrentZoom() {
        return currentZoom;
    }

    public boolean isZoomSupported() {
        return isZoomSupported;
    }

    public float getMaxZoom() {
        return maxZoom;
    }

    private void updateCameraZoom() {
        if (!isZoomSupported || camera == null) return;

        Camera.Parameters parameters = camera.getParameters();
        // Convert the normalized zoom value (0-1) to camera zoom value (0-maxZoom)
        int zoomValue = (int) (currentZoom * maxZoom);
        parameters.setZoom(zoomValue);

        try {
            camera.setParameters(parameters);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error setting zoom: " + e.getMessage());
        }
    }

    public void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }
}
