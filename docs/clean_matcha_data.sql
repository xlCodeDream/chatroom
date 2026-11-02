-- ============================================================
-- 抹茶相关数据清理脚本
-- 执行说明：在 MySQL 客户端中执行，需根据实际业务逻辑评估影响
-- ============================================================

-- 1. 删除抹茶全员群相关的消息标记
DELETE FROM message_mark
WHERE msg_id IN (
    SELECT id FROM message WHERE room_id = 1
);

-- 2. 删除抹茶全员群相关的消息
DELETE FROM message WHERE room_id = 1;

-- 3. 删除抹茶全员群相关的会话记录
DELETE FROM contact WHERE room_id = 1;

-- 4. 删除抹茶全员群的成员记录
DELETE FROM group_member WHERE group_id = 1;

-- 5. 删除抹茶全员群的群聊房间记录
DELETE FROM room_group WHERE id = 1 AND room_id = 1;

-- 6. 删除抹茶全员群的房间记录
DELETE FROM room WHERE id = 1;

-- 7. 删除用户角色关联中的抹茶群聊管理员记录
DELETE FROM user_role WHERE role_id = 2;

-- 8. 删除抹茶群聊管理员角色
DELETE FROM role WHERE id = 2;

-- 9. 删除抹茶徽章配置
DELETE FROM item_config WHERE id IN (3, 4, 5, 6);

-- ============================================================
-- 可选：验证清理结果
-- ============================================================
-- SELECT COUNT(*) FROM room WHERE id = 1;
-- SELECT COUNT(*) FROM room_group WHERE id = 1;
-- SELECT COUNT(*) FROM role WHERE id = 2;
-- SELECT COUNT(*) FROM item_config WHERE id IN (3,4,5,6);