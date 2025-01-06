package com.baidu.paddle.lite.demo.ppocr_demo;

import android.content.Context;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

public class Native {
    static {
        System.loadLibrary("Native");
    }

    private long ctx = 0;
    private RecTextResult run_status = null;

    public boolean init(Context mContext,
                        String detModelPath,
                        String clsModelPath,
                        String recModelPath,
                        String configPath,
                        String labelPath,
                        int cputThreadNum,
                        String cpuPowerMode) {
        ctx = nativeInit(
                detModelPath,
                clsModelPath,
                recModelPath,
                configPath,
                labelPath,
                cputThreadNum,
                cpuPowerMode);
        return ctx == 0;
    }

    public boolean release() {
        if (ctx == 0) {
            return false;
        }
        return nativeRelease(ctx);
    }

    public RecTextResult process(int inTextureId, int outTextureId, int textureWidth, int textureHeight, String savedImagePath) {
        if (ctx == 0) {
            return new RecTextResult();
        }
        run_status = nativeProcess(ctx, inTextureId, outTextureId, textureWidth, textureHeight, savedImagePath);
        return run_status;
    }


    public static native long nativeInit(String detModelPath,
                                         String clsModelPath,
                                         String recModelPath,
                                         String configPath,
                                         String labelPath,
                                         int cputThreadNum,
                                         String cpuPowerMode);

    public static native boolean nativeRelease(long ctx);

    public static native RecTextResult nativeProcess(long ctx, int inTextureId, int outTextureId, int textureWidth, int textureHeight, String savedImagePath);
}
