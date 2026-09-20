package com.company.orchestrator.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.company.orchestrator.ai.AiClient.Answer;
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuditService {
    private final JdbcTemplate db;
    @Value("${app.ai-audit.retention-days:90}") private int retentionDays;
    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void record(String type,long id,String input,Answer answer,String status,long duration) {
        db.update("insert into ai_execution(type,business_id,model,prompt_version,input,output,status,duration,token_usage) values (?,?,?,'v1',?,?,?,?,cast(? as jsonb))",
            type,Long.toString(id),answer==null?"unknown":answer.model(),input,answer==null?null:answer.text(),status,duration,
            answer==null?"{}":"{\"prompt\":"+answer.inputTokens()+",\"completion\":"+answer.outputTokens()+"}");
    }
    /** 保留期清理：超期每日清理，retention-days<=0 表示永久保留 / purge audit rows past the retention window; <=0 keeps everything. */
    @Scheduled(cron="${app.ai-audit.purge-cron:0 30 3 * * *}")
    public void purgeExpired() {
        if(retentionDays<=0) return;
        int purged=db.update("delete from ai_execution where created_at < now() - make_interval(days => ?)",retentionDays);
        if(purged>0) log.info("ai audit purged, rows={}, retentionDays={}",purged,retentionDays);
    }
}
