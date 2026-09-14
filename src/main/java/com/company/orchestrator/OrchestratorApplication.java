package com.company.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Resource Orchestrator 启动类。
 * 模块化单体：auth / employee / department / skill / project / task /
 * allocation / solver / ai / system / common。
 */
@SpringBootApplication
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
