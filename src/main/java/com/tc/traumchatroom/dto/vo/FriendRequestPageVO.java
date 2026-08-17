package com.tc.traumchatroom.dto.vo;

import com.tc.traumchatroom.dto.response.FriendRequestResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 好友申请列表分页响应 VO（含总数，用于未读红点计数）
 * 与 CursorPageVO 隔离，避免影响消息历史等其他接口
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendRequestPageVO {
    /** 当前页数据列表 */
    private List<FriendRequestResponse> items;
    /** 符合条件的申请总数 */
    private Long total;
    /** 是否还有更多数据 */
    private boolean hasMore;
}
