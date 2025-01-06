package com.baidu.paddle.lite.demo.ppocr_demo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RecTextResultProcessor {
    private final RecTextResult recTextResult;

    public RecTextResultProcessor(Builder builder) {
        this.recTextResult = builder.build().recTextResult;
    }

    public static class Builder {
        private RecTextResult recTextResult;
        private Map<String, Float> recTextResultMap = new HashMap<>();
        private final Map<String, Float> processedRecTextResultMap = new HashMap<>();

        public Builder setRecTextResult(RecTextResult recTextResult) {
            this.recTextResult = recTextResult;
            return this;
        }

        public Map<String, Float> getResultAsMap() {
            return processedRecTextResultMap;
        }

        public Map<String, Float> ignoreTextAndGetResultAsMap() {
            Map<String, Float> filteredResults = new HashMap<>();
            Set<Map.Entry<String, Float>> entrySet = processedRecTextResultMap.entrySet();
            for (Map.Entry<String, Float> entry : entrySet) {
                if (!isNumeric(entry.getKey())) {
                    filteredResults.put(entry.getKey(), entry.getValue());
                }
            }
            return filteredResults;
        }

        private Builder toMap() {
            Map<String, Float> map = new HashMap<>();
            for (int i = 0; i < recTextResult.getRecText().size(); i++) {
                map.put(recTextResult.getRecText().get(i), recTextResult.getRecTextScore().get(i));
            }
            this.recTextResultMap = map;
            return this;
        }

        public Builder process(float accuracyThreshold) {
            Set<Map.Entry<String, Float>> entrySet = toMap().recTextResultMap.entrySet();
            for (Map.Entry<String, Float> entry : entrySet) {
                if (entry.getValue() > accuracyThreshold) {
                    processedRecTextResultMap.put(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        public RecTextResultProcessor build() {
            return new RecTextResultProcessor(this);
        }
    }

    public static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}