package com.company.orchestrator.common.result;

import java.util.List;
import java.util.function.Function;

import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 统一分页响应：{list, total, pageNum, pageSize}。
 */
public record PageVO<T>(List<T> list, long total, long pageNum, long pageSize) {

    public static <T> PageVO<T> from(IPage<T> page) {
        return new PageVO<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public <R> PageVO<R> map(Function<T, R> mapper) {
        return new PageVO<>(list.stream().map(mapper).toList(), total, pageNum, pageSize);
    }
}
