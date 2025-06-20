package com.baidu.paddle.lite.demo.ppocr_demo;

import java.util.List;

public class RecTextResult {
    private final List<String> recText;
    private final List<Float> recTextScore;

    public RecTextResult() {
        this.recText = null;
        this.recTextScore = null;
    }

    public RecTextResult(List<String> recText, List<Float> recTextScore) {
        this.recText = recText;
        this.recTextScore = recTextScore;
    }

    public List<String> getRecText() {
        return recText;
    }

    public List<Float> getRecTextScore() {
        return recTextScore;
    }
}