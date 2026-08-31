-- ============================================================================
-- V2__user_last_login_ip.sql — user 表新增最近登录 IP 字段（存量库迁移）
--
-- 项目没有 Flyway/Liquibase，createTables.sql 只服务全新安装。
-- 存量库需要手工执行本脚本。
--
-- 幂等：通过 information_schema.columns 判断列是否存在后再执行，可重复运行。
-- 无锁风险说明：ADD COLUMN 默认 ALGORITHM=INPLACE, LOCK=NONE，不阻塞读写。
--
-- 用法：
--   mysql -h<host> -u<user> -p <dbname> < V2__user_last_login_ip.sql
--
-- 回滚：
--   ALTER TABLE `user` DROP COLUMN `last_login_ip`;
-- ============================================================================

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'user'
       AND column_name = 'last_login_ip') = 0,
    'ALTER TABLE `user` ADD COLUMN `last_login_ip` VARCHAR(45) DEFAULT NULL COMMENT ''最近一次登录IP（v4/v6兼容）''',
    'SELECT ''last_login_ip 已存在，跳过''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
