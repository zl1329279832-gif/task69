package com.service;

import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.entity.GaojianEntity;
import java.util.Map;

/**
 * 稿件 服务类
 */
public interface GaojianService extends IService<GaojianEntity> {

    /**
    * @param params 查询参数
    * @return 带分页的查询出来的数据
    */
     PageUtils queryPage(Map<String, Object> params);

    /**
     * 按学科自动匹配专家并分配
     * @param gaojianId 稿件ID
     * @return 匹配到的专家ID，如果没有合适专家返回null
     */
    Integer autoAssignExpert(Integer gaojianId);
}