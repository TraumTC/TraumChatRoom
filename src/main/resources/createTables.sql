-- User表   用户信息
CREATE TABLE user (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID，自增',
    username VARCHAR(20) NOT NULL UNIQUE COMMENT '登录用户名，2-20字符，唯一',
    name VARCHAR(20) NOT NULL UNIQUE COMMENT '显示昵称，1-20字符，唯一',
    password VARCHAR(120) NOT NULL COMMENT '密码，BCrypt加密后约60字符，预留120',
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER' COMMENT '角色: ROLE_USER/ROLE_ADMIN/ROLE_GUEST',
    avatar VARCHAR(500) DEFAULT NULL COMMENT '自定义头像URL，NULL则使用默认首字头像',
    status TINYINT DEFAULT 1 COMMENT '账号状态: 1正常 0禁用',
    last_login_ip VARCHAR(45) DEFAULT NULL COMMENT '最近一次登录IP（v4/v6兼容）',
    last_active_time TIMESTAMP DEFAULT NULL COMMENT '最后活跃时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at TIMESTAMP DEFAULT NULL COMMENT '软删除时间，NULL表示未删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- message表   聊天数据信息
CREATE TABLE `message` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID，BIGINT防溢出',
    `sender_id` INT DEFAULT NULL COMMENT '发送者ID，用户删除时置NULL（sender_name保留原始值）',
    `sender_name` VARCHAR(20) NOT NULL COMMENT '发送者昵称（冗余字段，避免JOIN）',
    `receiver_id` INT DEFAULT NULL COMMENT '接收者ID，群聊时为NULL',
    `receiver_name` VARCHAR(20) DEFAULT NULL COMMENT '接收者昵称（冗余字段）',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `message_type` VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '消息类型: text/file/image/system',
    `file_name` VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
    `file_path` VARCHAR(500) DEFAULT NULL COMMENT '文件存储路径',
    `file_size` BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
    `is_ai_reply` TINYINT(1) DEFAULT 0 COMMENT '是否为AI回复: 0否 1是',
    `reply_to_id` BIGINT DEFAULT NULL COMMENT '引用回复的消息ID',
    `is_recalled` TINYINT(1) DEFAULT 0 COMMENT '是否已撤回: 0否 1是',
    `recalled_at` TIMESTAMP DEFAULT NULL COMMENT '撤回时间',
    `original_content` TEXT DEFAULT NULL COMMENT '撤回前的原始内容（管理员可查看）',
    `sender_ip` VARCHAR(45) DEFAULT NULL COMMENT '发送者IP（v4/v6兼容）',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `deleted_at` TIMESTAMP DEFAULT NULL COMMENT '软删除时间',
    FOREIGN KEY (`sender_id`) REFERENCES `user`(`id`) ON DELETE SET NULL,
    -- 索引设计（针对高频查询场景）
    --
    -- 三条复合索引都把 deleted_at 纳入（所有查询都带 deleted_at IS NULL），
    -- 且末列用 id 而非 created_at —— 所有历史查询都是 ORDER BY m.id，
    -- 用 created_at 收尾无法支撑排序。
    --
    -- 注意 message 是本项目最热的写表（每条聊天消息一次 INSERT），
    -- 因此只保留有查询实际使用的索引：
    -- 不建 reply_to_id 索引 —— 引用回复由前端从已加载消息中解析
    -- （MessageItem.vue 的 quotedMsg），服务端从无按 reply_to_id 过滤的查询。
    INDEX `idx_group_history` (`receiver_id`, `deleted_at`, `id`)
        COMMENT '群聊历史：receiver_id IS NULL + 游标翻页/排序',
    INDEX `idx_private_history` (`sender_id`, `receiver_id`, `deleted_at`, `id`)
        COMMENT '私聊历史双向定位 + 会话最新消息ID（并为 sender_id 外键提供索引）',
    INDEX `idx_unread` (`receiver_id`, `sender_id`, `deleted_at`, `id`)
        COMMENT '离线未读统计 + 私聊会话对端列表（列序与 idx_private_history 相反，服务反向查询；实测覆盖索引，耗时减半）',
    INDEX `idx_created_at` (`created_at`) COMMENT '按时间排序（管理端统计）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- friend表  好友关系
CREATE TABLE `friend` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT NOT NULL COMMENT '用户ID',
    `friend_id` INT NOT NULL COMMENT '好友ID',
    `remark` VARCHAR(20) DEFAULT NULL COMMENT '好友备注名（A给B的备注）',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '成为好友时间',
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`friend_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`) COMMENT '防止重复添加',
    INDEX `idx_friend_id` (`friend_id`) COMMENT '反向查询：谁是我的好友'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- friend request表  好友申请表
CREATE TABLE `friend_request` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `sender_id` INT NOT NULL COMMENT '申请者ID',
    `receiver_id` INT NOT NULL COMMENT '接收者ID',
    `message` VARCHAR(100) DEFAULT NULL COMMENT '申请附言',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已同意 2已拒绝',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`sender_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`receiver_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    INDEX `idx_receiver_status` (`receiver_id`, `status`) COMMENT '查询待处理申请',
    INDEX `idx_sender` (`sender_id`) COMMENT '查询发出的申请'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ai_conversation_context表  AI对话上下文
CREATE TABLE `ai_conversation_context` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `session_key` VARCHAR(100) NOT NULL COMMENT '会话标识: group(群聊) / userA:userB(私聊)',
    `role` VARCHAR(20) NOT NULL COMMENT '消息角色: user / assistant',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- (session_key, id) 而非单列 session_key：deleteOld 的子查询是
    -- WHERE session_key=? ORDER BY id DESC LIMIT n，需要 id 在索引里才能免排序
    INDEX `idx_session_id` (`session_key`, `id`) COMMENT '按会话查上下文 + 裁剪旧记录',
    INDEX `idx_ctx_created_at` (`created_at`) COMMENT '定时清理陈旧会话'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- sensitive_word表 敏感词表
CREATE TABLE `sensitive_word` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `word` VARCHAR(50) NOT NULL UNIQUE COMMENT '敏感词',
    `level` TINYINT NOT NULL DEFAULT 1 COMMENT '1替换为*** 2拦截拒绝发送',
    `category` VARCHAR(20) DEFAULT NULL COMMENT '分类: insult(辱骂)/ad(广告)/spam(垃圾)/politics(政治)',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- operation_log表  操作日志
CREATE TABLE `operation_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT DEFAULT NULL COMMENT '操作者ID',
    `username` VARCHAR(20) DEFAULT NULL COMMENT '操作者用户名（冗余，防止用户被删后无法查看）',
    `action` VARCHAR(50) NOT NULL COMMENT '操作类型: LOGIN/LOGOUT/DELETE_USER等',
    `target_type` VARCHAR(20) DEFAULT NULL COMMENT '目标类型: user/message/file',
    `target_id` BIGINT DEFAULT NULL COMMENT '目标ID',
    `detail` TEXT DEFAULT NULL COMMENT '操作详情（JSON格式）',
    `ip` VARCHAR(45) DEFAULT NULL COMMENT '操作者IP地址',
    `user_agent` VARCHAR(500) DEFAULT NULL COMMENT '浏览器UA字符串',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_action` (`action`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 说明：原先这里有一个 trg_user_name_update 触发器，在 user.name 变更后同步改写
-- message.sender_name 与 message.receiver_name。已删除，原因（报告 P1-9）：
--
-- 1. receiver_name 列存的语义是 **username**（见 MessageMapper.xml / FileServiceImpl 注释），
--    触发器却拿 **昵称** 去匹配它：只要有用户把昵称改成恰好等于别人的 username，
--    所有发给那个人的私聊行 receiver_name 会被整片改写，导致会话记录错乱、消息串到别人会话。
-- 2. sender_name 那半句与 MessageMapper.updateSenderName 职责重复，而后者按 sender_id
--    精确定位，比按昵称字符串匹配安全。
--
-- 现在两条改名路径都在应用层同步 sender_name：
--   用户自己改名 → UserServiceImpl#updateProfile
--   管理员改名   → AdminController#updateUser
-- 存量库请执行 migration/V3__drop_user_name_trigger.sql 删除该触发器并修复被污染的数据。