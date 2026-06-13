-- ============================================================
-- V2: 学报稿件盲审流程 - 数据库变更（幂等版本）
-- 原始脚本: blind_review.sql
-- 本脚本可重复执行，不会因为字段/表/数据已存在而报错
-- ============================================================

-- -------------------------------------------------------
-- 辅助存储过程：安全添加列（列已存在则跳过）
-- -------------------------------------------------------
DROP PROCEDURE IF EXISTS safe_add_column;

DELIMITER //
CREATE PROCEDURE safe_add_column(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition VARCHAR(1024)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- -------------------------------------------------------
-- 1. gaojian 表新增字段
-- -------------------------------------------------------
CALL safe_add_column('gaojian', 'gaojian_status_types', "INT DEFAULT 1 COMMENT '稿件状态：1待审 2审稿中 3修回 4录用 5退稿'");
CALL safe_add_column('gaojian', 'gaojian_file_history', "TEXT COMMENT '历史文件版本JSON'");
CALL safe_add_column('gaojian', 'gaojian_yesno_text', "TEXT COMMENT '审稿意见链JSON'");

-- -------------------------------------------------------
-- 2. zhuanjia 表新增专业方向字段
-- -------------------------------------------------------
CALL safe_add_column('zhuanjia', 'zhuanjia_gaojian_types', "VARCHAR(255) COMMENT '专家擅长学科类型，逗号分隔的gaojian_types值'");

-- -------------------------------------------------------
-- 3. 利益冲突表（IF NOT EXISTS 天然幂等）
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS liyi_chongtu (
  id INT AUTO_INCREMENT PRIMARY KEY,
  zuozhe_id INT NOT NULL COMMENT '作者ID',
  zhuanjia_id INT NOT NULL COMMENT '专家ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_pair (zuozhe_id, zhuanjia_id)
) COMMENT='利益冲突关系表';

-- -------------------------------------------------------
-- 4. 字典表数据 - 稿件状态（NOT EXISTS 防重复插入）
-- -------------------------------------------------------
INSERT INTO dictionary(dic_code, dic_name, code_index, index_name, super_id, create_time)
SELECT 'gaojian_status_types','稿件状态',1,'待审',1,NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM dictionary WHERE dic_code='gaojian_status_types' AND code_index=1
);

INSERT INTO dictionary(dic_code, dic_name, code_index, index_name, super_id, create_time)
SELECT 'gaojian_status_types','稿件状态',2,'审稿中',1,NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM dictionary WHERE dic_code='gaojian_status_types' AND code_index=2
);

INSERT INTO dictionary(dic_code, dic_name, code_index, index_name, super_id, create_time)
SELECT 'gaojian_status_types','稿件状态',3,'修回',1,NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM dictionary WHERE dic_code='gaojian_status_types' AND code_index=3
);

INSERT INTO dictionary(dic_code, dic_name, code_index, index_name, super_id, create_time)
SELECT 'gaojian_status_types','稿件状态',4,'录用',1,NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM dictionary WHERE dic_code='gaojian_status_types' AND code_index=4
);

INSERT INTO dictionary(dic_code, dic_name, code_index, index_name, super_id, create_time)
SELECT 'gaojian_status_types','稿件状态',5,'退稿',1,NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM dictionary WHERE dic_code='gaojian_status_types' AND code_index=5
);

-- -------------------------------------------------------
-- 清理辅助存储过程
-- -------------------------------------------------------
DROP PROCEDURE IF EXISTS safe_add_column;
