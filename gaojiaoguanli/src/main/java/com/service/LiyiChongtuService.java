package com.service;

import com.baomidou.mybatisplus.service.IService;
import com.entity.LiyiChongtuEntity;
import com.utils.PageUtils;

import java.util.List;
import java.util.Map;

/**
 * 利益冲突关系 服务类
 */
public interface LiyiChongtuService extends IService<LiyiChongtuEntity> {

    PageUtils queryPage(Map<String, Object> params);

    /**
     * 查询与指定作者有利益冲突的专家ID列表
     */
    List<Integer> findConflictExpertIds(Integer zuozheId);
}
