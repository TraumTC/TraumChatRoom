-- ============================================================================
-- V3__drop_user_name_trigger.sql — 删除 trg_user_name_update 触发器 + 修复被它污染的数据
--
-- 项目没有 Flyway/Liquibase，createTables.sql 只服务全新安装。
-- 存量库需要手工执行本脚本。
--
-- 幂等：DROP TRIGGER IF EXISTS + 带 WHERE 条件的修复 UPDATE，可重复运行。
-- 无锁风险说明：DROP TRIGGER 只需一次短暂 MDL；修复 UPDATE 按 receiver_id 走
-- idx_private_history 定位，仅命中真正不一致的行，正常情况下为 0 行。
--
-- 用法：
--   mysql -h<host> -u<user> -p <dbname> < V3__drop_user_name_trigger.sql
--
-- 前置条件：先执行 V1__p2_indexes.sql。
--   V1 的「校验 B」统计的就是本脚本要修复的那批行，并在注释里指明
--   「建议尽早删除触发器」—— 本脚本即是那一步。
--
-- 回滚（不建议：这会重新引入 P1-9 的私聊路由污染）：
--   DELIMITER $$
--   CREATE TRIGGER trg_user_name_update AFTER UPDATE ON user FOR EACH ROW
--   BEGIN
--     IF OLD.name != NEW.name THEN
--       UPDATE message SET sender_name = NEW.name WHERE sender_name = OLD.name;
--       UPDATE message SET receiver_name = NEW.name WHERE receiver_name = OLD.name;
--     END IF;
--   END$$
--   DELIMITER ;
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 为什么删（报告 P1-9）
--
-- 触发器第二句：UPDATE message SET receiver_name = NEW.name WHERE receiver_name = OLD.name
--
-- receiver_name 列存的语义是 **username**（三个写入路径 WebSocketChatController /
-- FileServiceImpl / MessageBuilder 都写 username），触发器却拿 **昵称** 去匹配它。
-- 只要有用户把昵称改成恰好等于另一个用户的 username，所有发给后者的私聊行的
-- receiver_name 就会被整片改写 → 会话记录错乱、消息串到别人会话。
--
-- 第一句（sender_name）与 MessageMapper.updateSenderName 职责重复，
-- 而后者按 sender_id 精确定位，比按昵称字符串匹配安全。
-- 两条改名路径现已都在应用层同步 sender_name：
--   用户自己改名 → UserServiceImpl#updateProfile
--   管理员改名   → AdminController#updateUser（本次补齐，此前仅靠触发器）
-- ---------------------------------------------------------------------------

-- 修复前：统计将被修复的行数（只读）
SELECT COUNT(*) AS `修复前_receiver_name与username不符的行数`
FROM message m
JOIN user u ON u.id = m.receiver_id
WHERE m.receiver_id IS NOT NULL
  AND m.receiver_name <> u.username;

-- ---------------------------------------------------------------------------
-- 1) 删除触发器
-- ---------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_user_name_update;

-- ---------------------------------------------------------------------------
-- 2) 修复被触发器写坏的 receiver_name
--
-- receiver_id 是权威来源（外键指向 user.id，触发器从不动它），
-- 据此把 receiver_name 回填成正确的 username。
--
-- 为什么仍要修：selectPrivateHistory 已改按 receiver_id 定位，历史可见性不受影响，
-- 但 receiver_name 仍会通过 toMessageResponse 作为 receiver.username 回传给前端，
-- 而前端用它作私聊会话的桶 key —— 值不对会导致消息落错会话页签。
--
-- 只修 receiver_id 非空（私聊）的行；群聊行两列均为 NULL，不受触发器影响。
-- ---------------------------------------------------------------------------
UPDATE message m
JOIN user u ON u.id = m.receiver_id
SET m.receiver_name = u.username
WHERE m.receiver_id IS NOT NULL
  AND m.receiver_name <> u.username;

-- ---------------------------------------------------------------------------
-- 执行结果校验
-- ---------------------------------------------------------------------------

-- 应为 0：触发器已不存在
SELECT COUNT(*) AS `残留触发器数_应为0`
FROM information_schema.triggers
WHERE trigger_schema = DATABASE()
  AND trigger_name = 'trg_user_name_update';

-- 应为 0：receiver_name 与 username 已全部一致
SELECT COUNT(*) AS `修复后_receiver_name与username不符的行数_应为0`
FROM message m
JOIN user u ON u.id = m.receiver_id
WHERE m.receiver_id IS NOT NULL
  AND m.receiver_name <> u.username;

-- 提示项：receiver_id 指向已被硬删除用户的私聊行（JOIN 不到，上面的修复覆盖不到）。
-- message.sender_id 是 ON DELETE SET NULL、receiver_id 无外键动作，正常为 0。
SELECT COUNT(*) AS `提示_receiver_id查不到对应用户的私聊行`
FROM message m
LEFT JOIN user u ON u.id = m.receiver_id
WHERE m.receiver_id IS NOT NULL
  AND u.id IS NULL;
