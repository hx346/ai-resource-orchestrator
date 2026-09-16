package com.company.orchestrator.common.config;

import java.util.ArrayList;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 可选能力的 Flyway 位置：仅当 pgvector 语义检索开启（app.skill-semantic.enabled=true）
 * 时追加 db/migration-semantic（V900 起，避开主迁移序列，后启用不需要 out-of-order）。
 * 注意 Flyway 10+ 的 getLocations() 返回 Location[]，须取 descriptor 还原字符串。
 * Optional Flyway location: the pgvector semantic-search migration folder is only
 * added when the feature flag is on, keeping core deployments untouched. Flyway
 * 10+ exposes locations as Location[] — restore them via getDescriptor().
 */
@Configuration
public class FlywayConfig {

    private static final String SEMANTIC_LOCATION = "classpath:db/migration-semantic";

    @Bean
    @ConditionalOnProperty(prefix = "app.skill-semantic", name = "enabled", havingValue = "true")
    public FlywayConfigurationCustomizer semanticMigrationLocation() {
        return configuration -> {
            var locations = new ArrayList<String>();
            for (var location : configuration.getLocations()) locations.add(location.getDescriptor());
            locations.add(SEMANTIC_LOCATION);
            configuration.locations(locations.toArray(String[]::new));
        };
    }
}
