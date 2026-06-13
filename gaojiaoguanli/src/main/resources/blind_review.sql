-- ============================================================
-- 学报稿件盲审流程 - 数据库变更脚本
-- ============================================================

-- 1. gaojian 表新增字段
ALTER TABLE gaojian ADD COLUMN gaojian_status_types INT DEFAULT 1 COMMENT '稿件状态：1待审 2审稿中 3修回 4录用 5退稿';
ALTER TABLE gaojian ADD COLUMN gaojian_file_history TEXT COMMENT '历史文件版本JSON';
ALTER TABLE gaojian ADD COLUMN gaojian_yesno_text TEXT COMMENT '审稿意见链JSON';

-- 2. zhuanjia 表新增专业方向字段
ALTER TABLE zhuanjia ADD COLUMN zhuanjia_gaojian_types VARCHAR(255) COMMENT '专家擅长学科类型，逗号分隔的gaojian_types值';

-- 3. 利益冲突表（新表）
CREATE TABLE IF NOT EXISTS liyi_chongtu (
  id INT AUTO_INCREMENT PRIMARY KEY,
  zuozhe_id INT NOT NULL COMMENT '作者ID',
  zhuanjia_id INT NOT NULL COMMENT '专家ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_pair (zuozhe_id, zhuanjia_id)
) COMMENT='利益冲突关系表';

-- 4. 字典表数据 - 稿件状态
INSERT INTO dictionary(dic_code, dic_name, code_index, index_name, super_id, create_time)
VALUES
('gaojian_status_types','稿件状态',1,'待审',1,NOW()),
('gaojian_status_types','稿件状态',2,'审稿中',1,NOW()),
('gaojian_status_types','稿件状态',3,'修回',1,NOW()),
('gaojian_status_types','稿件状态',4,'录用',1,NOW()),
('gaojian_status_types','稿件状态',5,'退稿',1,NOW());
