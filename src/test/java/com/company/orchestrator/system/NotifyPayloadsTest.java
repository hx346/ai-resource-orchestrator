package com.company.orchestrator.system;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Provider payload formats: generic envelope plus feishu/dingtalk/wecom bot text bodies. */
class NotifyPayloadsTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void genericEnvelopeCarriesEventMessageData() throws Exception {
        var node = json.readTree(NotifyPayloads.body("generic", "PLAN_CONFIRMED", "hello", Map.of("items", 3)));
        assertEquals("PLAN_CONFIRMED", node.get("event").asText());
        assertEquals("hello", node.get("message").asText());
        assertEquals(3, node.get("data").get("items").asInt());
    }

    @Test
    void feishuUsesTextCard() throws Exception {
        var node = json.readTree(NotifyPayloads.body("feishu", "T", "方案已确认", Map.of()));
        assertEquals("text", node.get("msg_type").asText());
        assertEquals("方案已确认", node.get("content").get("text").asText());
    }

    @Test
    void dingtalkAndWecomShareTextBody() throws Exception {
        for (String provider : new String[] {"dingtalk", "wecom"}) {
            var node = json.readTree(NotifyPayloads.body(provider, "T", "方案已确认", Map.of()));
            assertEquals("text", node.get("msgtype").asText());
            assertEquals("方案已确认", node.get("text").get("content").asText());
        }
    }

    @Test
    void unknownOrNullFallsBackToGeneric() throws Exception {
        assertEquals(NotifyPayloads.body("generic", "T", "m", Map.of()), NotifyPayloads.body(null, "T", "m", Map.of()));
        assertEquals(NotifyPayloads.body("generic", "T", "m", Map.of()), NotifyPayloads.body("totally-unknown", "T", "m", Map.of()));
    }
}
