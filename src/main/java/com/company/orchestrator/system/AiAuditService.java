package com.company.orchestrator.system;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import com.company.orchestrator.ai.AiClient.Answer;
@Service @RequiredArgsConstructor
public class AiAuditService {
    private final JdbcTemplate db;
    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void record(String type,long id,String input,Answer answer,String status,long duration) {
        db.update("insert into ai_execution(type,business_id,model,prompt_version,input,output,status,duration,token_usage) values (?,?,?,'v1',?,?,?,?,cast(? as jsonb))",
            type,Long.toString(id),answer==null?"unknown":answer.model(),input,answer==null?null:answer.text(),status,duration,
            answer==null?"{}":"{\"prompt\":"+answer.inputTokens()+",\"completion\":"+answer.outputTokens()+"}");
    }
}
