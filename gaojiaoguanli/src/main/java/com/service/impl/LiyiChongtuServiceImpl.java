package com.service.impl;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import com.dao.LiyiChongtuDao;
import com.entity.LiyiChongtuEntity;
import com.service.LiyiChongtuService;
import com.utils.PageUtils;
import com.utils.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 利益冲突关系 服务实现类
 */
@Service("liyiChongtuService")
@Transactional
public class LiyiChongtuServiceImpl extends ServiceImpl<LiyiChongtuDao, LiyiChongtuEntity> implements LiyiChongtuService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        if (params != null && (params.get("limit") == null || params.get("page") == null)) {
            params.put("page", "1");
            params.put("limit", "10");
        }
        Page<LiyiChongtuEntity> page = new Query<LiyiChongtuEntity>(params).getPage();
        page.setRecords(baseMapper.selectPage(page, null));
        return new PageUtils(page);
    }

    @Override
    public List<Integer> findConflictExpertIds(Integer zuozheId) {
        List<LiyiChongtuEntity> list = baseMapper.selectList(
                new EntityWrapper<LiyiChongtuEntity>().eq("zuozhe_id", zuozheId)
        );
        List<Integer> expertIds = new ArrayList<Integer>();
        for (LiyiChongtuEntity item : list) {
            expertIds.add(item.getZhuanjiaId());
        }
        return expertIds;
    }
}
