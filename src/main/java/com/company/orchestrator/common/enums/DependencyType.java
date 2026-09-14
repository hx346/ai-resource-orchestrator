package com.company.orchestrator.common.enums;

/** 任务依赖类型（FS=完成-开始 等）/ Task dependency type. */
public enum DependencyType {
    /** Finish-to-Start 完成-开始 */
    FS,
    /** Start-to-Start 开始-开始 */
    SS,
    /** Finish-to-Finish 完成-完成 */
    FF,
    /** Start-to-Finish 开始-完成 */
    SF
}
