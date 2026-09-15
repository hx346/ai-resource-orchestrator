package com.company.orchestrator.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** LLM 输出清洗：思维链块与代码围栏 / model-output cleaning: reasoning blocks and fences. */
class AiTextTest {
    private static final String JSON="{\"summary\":\"s\",\"tasks\":[]}";

    @Test void plainJsonUnchanged() { assertEquals(JSON,AiText.clean(JSON)); }

    @Test void stripsFences() { assertEquals(JSON,AiText.clean("```json\n"+JSON+"\n```")); }

    @Test void stripsClosedThinkBlock() { // Qwen3 / DeepSeek-R1 典型输出 / typical reasoning-model output
        assertEquals(JSON,AiText.clean("<think>\n先推理一段\n</think>\n"+JSON)); }

    @Test void stripsTruncatedThinkBlock() { // 生成被截断时 </think> 缺失 / truncated block without closing tag
        assertEquals("",AiText.clean("<think>\n想了一半")); }

    @Test void stripsThinkAndFencesTogether() {
        assertEquals(JSON,AiText.clean("<think>推理</think>```json\n"+JSON+"\n```")); }

    @Test void stripsMultipleThinkBlocks() {
        assertEquals(JSON,AiText.clean("<think>a</think><think>b</think>"+JSON)); }

    @Test void nullBecomesEmpty() { assertEquals("",AiText.clean(null)); }
}
