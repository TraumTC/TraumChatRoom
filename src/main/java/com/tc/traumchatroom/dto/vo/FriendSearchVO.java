package com.tc.traumchatroom.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友搜索结果 VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendSearchVO {
    private Integer id;
    private String username;
    private String name;
    private String avatar;
    private String friendStatus;    // none / friend / pending_sent / pending_received
}
