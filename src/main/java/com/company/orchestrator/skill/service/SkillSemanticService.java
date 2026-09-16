package com.company.orchestrator.skill.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.company.orchestrator.skill.entity.Skill;
import com.company.orchestrator.skill.mapper.SkillMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 技能语义检索（Phase 3 余项，可选 pgvector 能力）：技能与查询词都向量化存入
 * skill_embedding，按余弦距离取近邻。local 模式用确定性字符组哈希（零外部依赖），
 * live 模式调用 OpenAI 兼容 /v1/embeddings。未启用时全部方法空转或报错，
 * 主链路（CRUD + 求解）完全不受影响。
 * Semantic skill search (Phase 3 remainder, optional pgvector): skills and
 * queries are embedded into skill_embedding and matched by cosine distance.
 * `local` uses a deterministic char-gram hash (no external dependency); `live`
 * calls an OpenAI-compatible /v1/embeddings endpoint. Disabled by default —
 * the core CRUD + solver pipeline is unaffected.
 */
@Slf4j
@Service
public class SkillSemanticService {

    /** 未匹配名的建议下限：低于该余弦不提示（哈希嵌入下近形名约 0.5+，无关名近 0）/ suggestion bar: morphological relatives score ~0.5+, unrelated ~0. */
    public static final double SUGGEST_MIN = 0.5;

    private final JdbcTemplate db;
    private final SkillMapper skills;
    private final ObjectProvider<EmbeddingModel> embeddingModels;
    @Value("${app.skill-semantic.enabled:false}") private boolean enabled;
    @Value("${app.skill-semantic.embedding-mode:local}") private String mode;
    @Value("${app.skill-semantic.dim:256}") private int dim;
    @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") private String liveModel;

    public SkillSemanticService(JdbcTemplate db, SkillMapper skills, ObjectProvider<EmbeddingModel> embeddingModels) {
        this.db = db;
        this.skills = skills;
        this.embeddingModels = embeddingModels;
    }

    public boolean enabled() { return enabled; }

    public Map<String, Object> status() {
        Long count = enabled ? db.queryForObject("select count(*) from skill_embedding", Long.class) : 0L;
        return Map.of("enabled", enabled, "embeddingMode", enabled ? mode : "off",
                "model", modelLabel(), "dim", dim, "embeddedSkills", count == null ? 0 : count);
    }

    /** 重建全部技能向量（切换嵌入模式后执行）/ rebuild all skill vectors. */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rebuild() {
        requireEnabled();
        int written = 0;
        for (var skill : skills.selectList(new LambdaQueryWrapper<Skill>().eq(Skill::getStatus, Skill.STATUS_ACTIVE))) {
            store(skill.getId(), skill.getName());
            written++;
        }
        log.info("skill embeddings rebuilt, size={}, mode={}", written, modelLabel());
        return Map.of("rebuilt", written, "model", modelLabel());
    }

    /** 语义检索：返回带余弦得分的近邻 / top semantic neighbours with cosine scores. */
    public List<Map<String, Object>> search(String query, int limit) {
        requireEnabled();
        if (query == null || query.isBlank()) return List.of();
        String literal = SemanticEmbedding.literal(embed(query));
        return db.queryForList("""
                select s.id "skillId", s.name "skillName", 1 - (se.embedding <=> cast(? as vector)) "score"
                from skill_embedding se join skill s on s.id = se.skill_id
                where se.model = ? and s.status = 'ACTIVE'
                order by se.embedding <=> cast(? as vector)
                limit ?""", literal, modelLabel(), literal, Math.max(1, Math.min(limit, 50)));
    }

    /** 未匹配技能名的语义建议；未启用或低于阈值返回 null / vector suggestion for unmatched names. */
    public Suggestion suggest(String name) {
        if (!enabled || name == null || name.isBlank()) return null;
        var rows = search(name, 1);
        if (rows.isEmpty()) return null;
        double score = ((Number) rows.getFirst().get("score")).doubleValue();
        if (score < SUGGEST_MIN) return null;
        return new Suggestion(((Number) rows.getFirst().get("skillId")).longValue(), rows.getFirst().get("skillName").toString(), score);
    }

    public record Suggestion(long skillId, String skillName, double score) {}

    private void store(long skillId, String name) {
        float[] vector = embed(name);
        db.update("""
                insert into skill_embedding(skill_id, model, dim, embedding, updated_at) values (?,?,?,cast(? as vector),now())
                on conflict (skill_id) do update set model=excluded.model, dim=excluded.dim,
                    embedding=excluded.embedding, updated_at=now()""",
                skillId, modelLabel(), vector.length, SemanticEmbedding.literal(vector));
    }

    private float[] embed(String text) {
        if (!"live".equals(mode)) return SemanticEmbedding.hash(text, dim);
        EmbeddingModel model = embeddingModels.getIfAvailable();
        if (model == null) throw new BusinessException(ErrorCode.AI_NO_PROVIDER);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            return executor.submit(() -> model.embed(text)).get(30, TimeUnit.SECONDS);
        } catch (TimeoutException ex) { throw new BusinessException(ErrorCode.AI_TIMEOUT); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new BusinessException(ErrorCode.AI_CANCELLED); }
        catch (ExecutionException ex) { throw new BusinessException(ErrorCode.AI_UPSTREAM_FAILED); }
        finally { executor.shutdownNow(); }
    }

    private String modelLabel() { return "live".equals(mode) ? liveModel : "local-hash"; }

    private void requireEnabled() {
        if (!enabled) throw new BusinessException(ErrorCode.BAD_REQUEST, "语义检索未启用：设置 SKILL_SEMANTIC_ENABLED=true 并使用支持 pgvector 的 PostgreSQL");
    }
}
