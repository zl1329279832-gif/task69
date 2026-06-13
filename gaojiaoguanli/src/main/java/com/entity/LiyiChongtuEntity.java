package com.entity;

import com.baomidou.mybatisplus.annotations.TableId;
import com.baomidou.mybatisplus.annotations.TableName;
import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.enums.IdType;

import java.io.Serializable;
import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 利益冲突关系
 */
@TableName("liyi_chongtu")
public class LiyiChongtuEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @TableField(value = "id")
    private Integer id;

    /**
     * 作者ID
     */
    @TableField(value = "zuozhe_id")
    private Integer zuozheId;

    /**
     * 专家ID
     */
    @TableField(value = "zhuanjia_id")
    private Integer zhuanjiaId;

    /**
     * 创建时间
     */
    @JsonFormat(locale = "zh", timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat
    @TableField(value = "create_time")
    private Date createTime;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getZuozheId() {
        return zuozheId;
    }

    public void setZuozheId(Integer zuozheId) {
        this.zuozheId = zuozheId;
    }

    public Integer getZhuanjiaId() {
        return zhuanjiaId;
    }

    public void setZhuanjiaId(Integer zhuanjiaId) {
        this.zhuanjiaId = zhuanjiaId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "LiyiChongtu{" +
                "id=" + id +
                ", zuozheId=" + zuozheId +
                ", zhuanjiaId=" + zhuanjiaId +
                ", createTime=" + createTime +
                "}";
    }
}
