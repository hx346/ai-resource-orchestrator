package com.company.orchestrator.skill.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 批量嵌入：live 模式按批调用、顺序保持、未配置提供方报错 / batched live embeddings. */
class SkillSemanticBatchTest {

    @SuppressWarnings("unchecked")
    private SkillSemanticService service(EmbeddingModel model, String mode) {
        ObjectProvider<EmbeddingModel> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(model);
        var service = new SkillSemanticService(null, null, provider);
        ReflectionTestUtils.setField(service, "mode", mode);
        ReflectionTestUtils.setField(service, "dim", 64);
        return service;
    }

    @Test
    void liveModeChunksRequestsAndKeepsOrder() {
        var calls = new AtomicInteger();
        EmbeddingModel model = new EmbeddingModel() {
            @Override public org.springframework.ai.embedding.EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
                throw new UnsupportedOperationException();
            }
            @Override public float[] embed(org.springframework.ai.document.Document document) {
                throw new UnsupportedOperationException();
            }
            @Override public List<float[]> embed(List<String> texts) {
                calls.incrementAndGet();
                return texts.stream().map(t -> SemanticEmbedding.hash(t, 64)).toList();
            }
        };
        var service = service(model, "live");
        var names = java.util.stream.IntStream.rangeClosed(1, 130).mapToObj(i -> "skill-" + i).toList();
        var vectors = service.embedAll(names);
        // 130 项按 64/批 → 3 次调用；顺序与输入一致 / 130 items chunk into 3 ordered calls
        assertEquals(130, vectors.size());
        assertEquals(3, calls.get());
        for (int i = 0; i < names.size(); i++)
            assertEquals(SemanticEmbedding.hash(names.get(i), 64)[0], vectors.get(i)[0], 1e-9);
    }

    @Test
    void liveModeWithoutProviderFailsTyped() {
        var service = service(null, "live");
        var ex = assertThrows(BusinessException.class, () -> service.embedAll(List.of("Java")));
        assertEquals(ErrorCode.AI_NO_PROVIDER, ex.getErrorCode());
    }

    @Test
    void localModeNeverCallsTheProvider() {
        EmbeddingModel model = Mockito.mock(EmbeddingModel.class);
        var vectors = service(model, "local").embedAll(List.of("Java", "PostgreSQL"));
        assertEquals(2, vectors.size());
        assertEquals(1.0, SemanticEmbedding.cosine(vectors.get(0), SemanticEmbedding.hash("Java", 64)), 1e-6);
        Mockito.verifyNoInteractions(model);
    }
}
