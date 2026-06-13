-- =============================================
-- 审稿流程数据库变更脚本
-- =============================================

-- 1. gaojian 表新增字段
ALTER TABLE `gaojian` ADD COLUMN `gaojian_yesno_text` TEXT COMMENT '审稿意见链' AFTER `gaojian_shenhe_content`;
ALTER TABLE `gaojian` ADD COLUMN `gaojian_file_history` TEXT COMMENT '稿件历史版本(分号分隔)' AFTER `gaojian_yesno_text`;

-- 2. zhuanjia 表新增学科类型字段(与 gaojian_types 共用字典)
ALTER TABLE `zhuanjia` ADD COLUMN `zhuanjia_types` INT COMMENT '专家学科类型' AFTER `zhuanjia_photo`;

-- 3. 更新审稿结果字典值 (gaojian_yesno_types)
--    先删除旧值，再插入新状态定义
DELETE FROM `dictionary` WHERE `dic_code` = 'gaojian_yesno_types';
INSERT INTO `dictionary` (`dic_code`, `dic_name`, `code_index`, `index_name`, `super_id`, `create_time`) VALUES
('gaojian_yesno_types', '审稿状态', 1, '待审',   NULL, NOW()),
('gaojian_yesno_types', '审稿状态', 2, '审稿中', NULL, NOW()),
('gaojian_yesno_types', '审稿状态', 3, '修回',   NULL, NOW()),
('gaojian_yesno_types', '审稿状态', 4, '录用',   NULL, NOW()),
('gaojian_yesno_types', '审稿状态', 5, '退稿',   NULL, NOW());
