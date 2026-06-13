-- ============================================================
-- 学报稿件盲审流程 - 数据库变更脚本（幂等版本）
-- 可安全重复执行，不会报错或产生重复数据
-- ============================================================

-- ============================================================
-- 1. gaojian 表新增字段（幂等：先检查再 ALTER）
-- ============================================================

-- 1.1 gaojian_status_types
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gaojian'
    AND COLUMN_NAME = 'gaojian_status_types');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE gaojian ADD COLUMN gaojian_status_types INT DEFAULT 1 COMMENT ''稿件状态：1待审 2审稿中 3修回 4录用 5退稿''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 gaojian_file_history
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gaojian'
    AND COLUMN_NAME = 'gaojian_file_history');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE gaojian ADD COLUMN gaojian_file_history TEXT COMMENT ''历史文件版本JSON''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 gaojian_yesno_text
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gaojian'
    AND COLUMN_NAME = 'gaojian_yesno_text');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE gaojian ADD COLUMN gaojian_yesno_text TEXT COMMENT ''审稿意见链JSON''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. zhuanjia 表新增专业方向字段（幂等）
-- ============================================================
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'zhuanjia'
    AND COLUMN_NAME = 'zhuanjia_gaojian_types');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE zhuanjia ADD COLUMN zhuanjia_gaojian_types VARCHAR(255) COMMENT ''专家擅长学科类型，逗号分隔的gaojian_types值''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. 利益冲突表（新表 — 已幂等）
-- ============================================================
CREATE TABLE IF NOT EXISTS liyi_chongtu (
  id INT AUTO_INCREMENT PRIMARY KEY,
  zuozhe_id INT NOT NULL COMMENT '作者ID',
  zhuanjia_id INT NOT NULL COMMENT '专家ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_pair (zuozhe_id, zhuanjia_id)
) COMMENT='利益冲突关系表';

-- ============================================================
-- 4. 字典表数据 - 稿件状态（幂等：WHERE NOT EXISTS 去重）
-- ============================================================

INSERT INTO dictionary(dic_code, dic_name, code_index, index_name, super_id, create_time)
SELECT 'gaojian_status_types', '稿件状态', 1, '待审', 1, NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM dictionary WHERE dic_code = 'gaojian_status_types' AND code_index = 1
);

INSERT INTO dictionary(dic_code, dic_name, code_index, index_name, super_id, create_time)
SELECT 'gaojian_status_types', '稿件状态', 2, '审稿中', 1, NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM dictionary WHERE dic_code = 'gaojian_status_types' AND code_index = 2
);

INSERT INTO dictionary(dic_code, dic_name, code_index, index_name, super_id, create_time)
SELECT 'gaojian_status_types', '稿件状态', 3, '修回', 1, NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM dictionary WHERE dic_code = 'gaojian_status_types' AND code_index = 3
);

INSERT INTO dictionary(dic_code, dic_name, code_index, index_name, super_id, create_time)
SELECT 'gaojian_status_types', '稿件状态', 4, '录用', 1, NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM dictionary WHERE dic_code = 'gaojian_status_types' AND code_index = 4
);

INSERT INTO dictionary(dic_code, dic_name, code_index, index_name, super_id, create_time)
SELECT 'gaojian_status_types', '稿件状态', 5, '退稿', 1, NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM dictionary WHERE dic_code = 'gaojian_status_types' AND code_index = 5
);
