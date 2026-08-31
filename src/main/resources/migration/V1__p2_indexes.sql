-- ============================================================================
-- V1__p2_indexes.sql — P2-20 / P2-23 索引重建（存量库迁移）
--
-- 项目没有 Flyway/Liquibase，createTables.sql 只服务全新安装。
-- 存量库需要手工执行本脚本。
--
-- 幂等：全部通过 information_schema 判断后再执行，可重复运行。
-- 无锁风险说明：MySQL 8.0 的二级索引增删默认 ALGORITHM=INPLACE, LOCK=NONE，
-- 不阻塞读写；message 表很大时仍建议在低峰执行（DDL 首尾各需一次短暂 MDL，
-- 若此时存在长事务，后续查询会排队在 DDL 之后）。
--
-- 用法：
--   mysql -h<host> -u<user> -p <dbname> < V1__p2_indexes.sql
--
-- 输出的前两行是前置校验结果：
--   A_孤儿私聊行 必须为 0 —— 不为 0 则先停下修数据（见下方说明）
--   B_receiver_name与username不符 仅作提示，不阻断
--
-- 回滚（同样先 ADD 后 DROP，因 sender_id 外键需要前导列索引）：
--   ALTER TABLE message ADD INDEX idx_group_chat (receiver_id, created_at),
--                       ADD INDEX idx_private_chat (sender_id, receiver_id, created_at);
--   ALTER TABLE message DROP INDEX idx_group_history,
--                       DROP INDEX idx_private_history, DROP INDEX idx_unread;
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 前置校验（两项，都在索引变更之前执行；只读，不修改任何数据）
--
-- 背景：selectPrivateHistory 已从按 receiver_name（字符串）定位改为按 receiver_id。
-- 两列由三个写入路径（WebSocketChatController / FileServiceImpl / MessageBuilder）
-- 始终同时写入，正常情况下必然一致。以下两项用于确认存量数据确实如此。
--
-- 校验 A —— 阻断项：receiver_id 为空但 receiver_name 有值的「孤儿私聊行」。
--   这类行旧代码能在私聊历史里查到（按 receiver_name 匹配），
--   改按 receiver_id 后会查不到 —— 即用户会丢失这部分私聊历史的可见性。
--   （它们同时还会因 receiver_id IS NULL 而混进群聊历史，属改动前就存在的问题。）
--   必须为 0；不为 0 则先回填 receiver_id 再执行本脚本。
--
-- 校验 B —— 提示项：receiver_name 与 user.username 对不上的行。
--   成因是 trg_user_name_update 触发器把 receiver_name 改写成了昵称（见报告 P1-9）。
--   这类行属于「旧代码查不到、新代码反而能查到」，切换后是修复而非风险，
--   因此不阻断执行；数值 > 0 只说明该触发器已经污染过数据，建议尽早删除触发器。
-- ---------------------------------------------------------------------------

-- 校验 A（必须为 0）
SELECT COUNT(*) AS `A_孤儿私聊行_receiver_id为空但name有值_必须为0`
FROM message
WHERE receiver_id IS NULL
  AND receiver_name IS NOT NULL;

-- 校验 B（仅提示，>0 不阻断）
SELECT COUNT(*) AS `B_receiver_name与username不符_仅提示`
FROM message m
LEFT JOIN user u ON u.id = m.receiver_id
WHERE m.receiver_id IS NOT NULL
  AND (u.username IS NULL OR m.receiver_name <> u.username);

-- ---------------------------------------------------------------------------
-- message 表索引
--
-- 顺序要求：必须「先全部 ADD、再 DROP 旧索引」，不可调换。
-- sender_id 上有外键 (FOREIGN KEY REFERENCES user(id))，InnoDB 要求始终存在
-- 一个以 sender_id 为前导列的索引。先删 idx_private_chat 会直接报
-- ERROR 1553 Cannot drop index ... needed in a foreign key constraint。
-- 按本脚本顺序，idx_private_history 先建好接管该职责，删除才能成功。
-- ---------------------------------------------------------------------------

-- 新增 idx_group_history (receiver_id, deleted_at, id)
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'message'
       AND index_name = 'idx_group_history') = 0,
    'ALTER TABLE `message` ADD INDEX `idx_group_history` (`receiver_id`, `deleted_at`, `id`)',
    'SELECT ''idx_group_history 已存在，跳过''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 新增 idx_private_history (sender_id, receiver_id, deleted_at, id)
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'message'
       AND index_name = 'idx_private_history') = 0,
    'ALTER TABLE `message` ADD INDEX `idx_private_history` (`sender_id`, `receiver_id`, `deleted_at`, `id`)',
    'SELECT ''idx_private_history 已存在，跳过''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 新增 idx_unread (receiver_id, sender_id, deleted_at, id)
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'message'
       AND index_name = 'idx_unread') = 0,
    'ALTER TABLE `message` ADD INDEX `idx_unread` (`receiver_id`, `sender_id`, `deleted_at`, `id`)',
    'SELECT ''idx_unread 已存在，跳过''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 注意：不新增 reply_to_id 索引。
-- 报告 P2-20 称「reply_to_id 无索引 → 引用回复查询走全表」，但实际不存在这样的查询：
-- reply_to_id 只被写入与原样回传，引用内容由前端从已加载消息里解析
-- （ChatRoomVue/src/components/chat/MessageItem.vue 的 quotedMsg）。
-- message 是本项目最热的写表，加一个没有读路径的索引只会拖慢每条消息的 INSERT。

-- 删除被完全覆盖的旧索引 idx_group_chat (receiver_id, created_at)
-- 新的 idx_group_history 含相同前导列 receiver_id；无查询按 (receiver_id, created_at) 排序
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'message'
       AND index_name = 'idx_group_chat') > 0,
    'ALTER TABLE `message` DROP INDEX `idx_group_chat`',
    'SELECT ''idx_group_chat 不存在，跳过''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 删除被完全覆盖的旧索引 idx_private_chat (sender_id, receiver_id, created_at)
-- 新的 idx_private_history 含相同前导列；无查询按 created_at 在会话内排序
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'message'
       AND index_name = 'idx_private_chat') > 0,
    'ALTER TABLE `message` DROP INDEX `idx_private_chat`',
    'SELECT ''idx_private_chat 不存在，跳过''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- ai_conversation_context 表索引（P2-23）
-- ---------------------------------------------------------------------------

-- 新增 idx_session_id (session_key, id)：deleteOld 子查询需 id 在索引内以免排序
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'ai_conversation_context'
       AND index_name = 'idx_session_id') = 0,
    'ALTER TABLE `ai_conversation_context` ADD INDEX `idx_session_id` (`session_key`, `id`)',
    'SELECT ''idx_session_id 已存在，跳过''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 新增 idx_ctx_created_at (created_at)：定时清理陈旧会话按时间扫描
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'ai_conversation_context'
       AND index_name = 'idx_ctx_created_at') = 0,
    'ALTER TABLE `ai_conversation_context` ADD INDEX `idx_ctx_created_at` (`created_at`)',
    'SELECT ''idx_ctx_created_at 已存在，跳过''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 删除被 idx_session_id 覆盖的单列索引 idx_session_key
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'ai_conversation_context'
       AND index_name = 'idx_session_key') > 0,
    'ALTER TABLE `ai_conversation_context` DROP INDEX `idx_session_key`',
    'SELECT ''idx_session_key 不存在，跳过''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 执行结果
-- ---------------------------------------------------------------------------
SELECT table_name AS `表`, index_name AS `索引`,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS `列`
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('message', 'ai_conversation_context')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
