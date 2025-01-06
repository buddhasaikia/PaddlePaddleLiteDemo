package com.baidu.paddle.lite.demo.ppocr_demo;

import java.util.HashMap;
import java.util.Map;

public class RecTextResultProcessor {
    private final RecTextResult recTextResult;

    public RecTextResultProcessor(Builder builder) {
        this.recTextResult = builder.build().recTextResult;
    }

    public RecTextResult getRecTextResult() {
        return recTextResult;
    }

    public static class Builder {
        private RecTextResult recTextResult;
        private Map<String, Float> recTextResultMap = new HashMap<>();
        private Map<String, Float> processedRecTextResultMap = new HashMap<>();

        public Builder setRecTextResult(RecTextResult recTextResult) {
            this.recTextResult = recTextResult;
            return this;
        }

        public Map<String, Float> getRecTextResultMap() {
            return recTextResultMap;
        }

        public Builder toMap(RecTextResult recTextResult) {
            Map<String, Float> map = new HashMap<>();
            for (int i = 0; i < recTextResult.getRecText().size(); i++) {
                map.put(recTextResult.getRecText().get(i), recTextResult.getRecTextScore().get(i));
            }
            this.recTextResultMap = map;
            return this;
        }

        public Builder process() {
            recTextResultMap.forEach((k, v) -> {
                if (v > 0.5) {
                    processedRecTextResultMap.put(k, v);
                }
            });
            return this;
        }

        public RecTextResultProcessor build() {
            return new RecTextResultProcessor(this);
        }
    }
}