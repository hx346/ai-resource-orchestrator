package com.company.orchestrator.ai;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.company.orchestrator.common.exception.*;
/** Pure LLM adapter: no persistence or business decisions. No implicit simulated output. */
@Component
public class AiClient {
    private final ObjectProvider<ChatModel> models;
    private final String mode;
    private final Semaphore slots=new Semaphore(4);
    public AiClient(ObjectProvider<ChatModel> models,@Value("${app.ai-mode:off}") String mode) { this.models=models; this.mode=mode; }
    public String mode() { return mode; }
    public record Answer(String text,String model,long inputTokens,long outputTokens) {}
    public Answer complete(String system,String input) {
        if(!"live".equals(mode)) throw new BusinessException(ErrorCode.AI_NOT_ENABLED);
        ChatModel model=models.getIfAvailable();
        if(model==null) throw new BusinessException(ErrorCode.AI_NO_PROVIDER);
        if(!slots.tryAcquire()) throw new BusinessException(ErrorCode.AI_BUSY);
        var executor=Executors.newVirtualThreadPerTaskExecutor();
        Future<Answer> future=executor.submit(() -> {
            try {
                var response=model.call(new Prompt(List.of(new SystemMessage(system),new UserMessage(input))));
                var usage=response.getMetadata().getUsage();
                return new Answer(response.getResult().getOutput().getText(),response.getMetadata().getModel(),usage.getPromptTokens(),usage.getCompletionTokens());
            } finally { slots.release(); }
        });
        try { return future.get(75,TimeUnit.SECONDS); }
        catch(TimeoutException ex) { future.cancel(true); throw new BusinessException(ErrorCode.AI_TIMEOUT); }
        catch(InterruptedException ex) { future.cancel(true); Thread.currentThread().interrupt(); throw new BusinessException(ErrorCode.AI_CANCELLED); }
        catch(ExecutionException ex) { throw new BusinessException(ErrorCode.AI_UPSTREAM_FAILED); }
        finally { executor.shutdownNow(); }
    }
}
