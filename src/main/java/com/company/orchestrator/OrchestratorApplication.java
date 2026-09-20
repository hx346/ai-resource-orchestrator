package com.company.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI Resource Orchestrator 启动类。
 * 模块化单体：auth / employee / department / skill / project / task /
 * allocation / solver / ai / system / common。
 */
@SpringBootApplication
@EnableScheduling // 定时任务（AI 审计保留清理等）/ scheduled jobs (audit retention purge)
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
