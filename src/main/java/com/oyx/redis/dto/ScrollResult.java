package com.oyx.redis.dto;

import lombok.Data;

import java.util.List;

/**
 * 用于封装滚动分页的结果
 */
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
