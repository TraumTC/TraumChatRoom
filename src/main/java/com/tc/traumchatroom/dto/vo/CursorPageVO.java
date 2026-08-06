package com.tc.traumchatroom.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 游标分页响应 VO
 * 用于消息历史等大数据量分页
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CursorPageVO<T> {
    /** 当前页数据列表 */
    private List<T> items;
    /** 下一页的游标（最后一条记录的ID） */
    private Long nextCursor;
    /** 是否还有更多数据 */
    private boolean hasMore;
}
