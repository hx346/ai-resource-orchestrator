package com.company.orchestrator.system;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 出站通知报文构造：按提供方生成群机器人文本格式，未知提供方回退通用 JSON。
 * Outbound notification bodies per provider; unknown providers fall back
 * to a generic JSON envelope.
 */
public final class NotifyPayloads {

    private static final ObjectMapper JSON = new ObjectMapper();

    private NotifyPayloads() {}

    public static String body(String provider, String type, String message, Object data) {
        return switch (provider == null ? "generic" : provider) {
            case "feishu" -> encode(Map.of("msg_type", "text", "content", Map.of("text", message)));
            case "dingtalk", "wecom" -> encode(Map.of("msgtype", "text", "text", Map.of("content", message)));
            default -> encode(Map.of("event", type, "message", message, "data", data));
        };
    }

    private static String encode(Object value) {
        try { return JSON.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
