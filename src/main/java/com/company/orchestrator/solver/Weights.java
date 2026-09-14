package com.company.orchestrator.solver;
import java.util.*;
/** Strategy-dependent soft-constraint weights, shared by every assignment of one solve. / 随策略变化的软约束权重，单次求解内所有实体共享同一实例。 */
public record Weights(int unassigned,int skill,int balance) {
    public static final Set<String> STRATEGIES=Set.of("BALANCED","BEST_SKILL_MATCH","LOWEST_RISK");
    public static Weights of(String strategy) {
        return switch(strategy==null?"BALANCED":strategy) {
            case "BEST_SKILL_MATCH" -> new Weights(10000,4,0);
            case "LOWEST_RISK" -> new Weights(10000,1,1);
            case "BALANCED" -> new Weights(10000,1,0);
            default -> throw new IllegalArgumentException("不支持的策略："+strategy);
        };
    }
}
