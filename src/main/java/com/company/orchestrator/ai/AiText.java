package com.company.orchestrator.ai;

/** LLM 输出清洗：剥思维链 <think> 块（兼容截断未闭合）与代码围栏 / Clean model output before JSON parsing: reasoning <think> blocks (tolerating truncation) and code fences. */
public final class AiText {
    private AiText() {}

    public static String clean(String text) {
        if(text==null) return "";
        return text.trim().replaceAll("(?s)<think>.*?(?:</think>|$)","").replaceAll("^```(?:json)?\\s*|\\s*```$","").trim();
    }
}
