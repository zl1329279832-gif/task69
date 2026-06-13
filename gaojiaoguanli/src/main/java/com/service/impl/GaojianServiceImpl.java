package com.service.impl;

import com.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.lang.reflect.Field;
import java.util.*;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;
import com.utils.PageUtils;
import com.utils.Query;
import org.springframework.web.context.ContextLoader;
import javax.servlet.ServletContext;

import com.dao.GaojianDao;
import com.entity.GaojianEntity;
import com.entity.ZhuanjiaEntity;
import com.service.GaojianService;
import com.service.LiyiChongtuService;
import com.service.ZhuanjiaService;
import com.entity.view.GaojianView;

/**
 * 稿件 服务实现类
 */
@Service("gaojianService")
@Transactional
public class GaojianServiceImpl extends ServiceImpl<GaojianDao, GaojianEntity> implements GaojianService {

    @Autowired
    private ZhuanjiaService zhuanjiaService;

    @Autowired
    private LiyiChongtuService liyiChongtuService;

    @Override
    public PageUtils queryPage(Map<String,Object> params) {
        if(params != null && (params.get("limit") == null || params.get("page") == null)){
            params.put("page","1");
            params.put("limit","10");
        }
        Page<GaojianView> page =new Query<GaojianView>(params).getPage();
        page.setRecords(baseMapper.selectListView(page,params));
        return new PageUtils(page);
    }

    @Override
    public Integer autoAssignExpert(Integer gaojianId) {
        GaojianEntity gaojian = baseMapper.selectById(gaojianId);
        if (gaojian == null || gaojian.getGaojianTypes() == null) {
            return null;
        }

        // 获取与作者有利益冲突的专家ID列表
        List<Integer> conflictIds = liyiChongtuService.findConflictExpertIds(gaojian.getZuozheId());

        // 查询所有专家
        List<ZhuanjiaEntity> allExperts = zhuanjiaService.selectList(null);

        // 过滤：学科匹配 + 无利益冲突
        String targetType = String.valueOf(gaojian.getGaojianTypes());
        List<ZhuanjiaEntity> candidates = new ArrayList<ZhuanjiaEntity>();
        for (ZhuanjiaEntity expert : allExperts) {
            // 排除利益冲突
            if (conflictIds.contains(expert.getId())) {
                continue;
            }
            // 检查学科匹配
            String expertTypes = expert.getZhuanjiaGaojianTypes();
            if (expertTypes != null && !expertTypes.isEmpty()) {
                String[] types = expertTypes.split(",");
                for (String t : types) {
                    if (t.trim().equals(targetType)) {
                        candidates.add(expert);
                        break;
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            // 如果没有学科匹配的专家，从非冲突专家中随机选一个（降级分配）
            for (ZhuanjiaEntity expert : allExperts) {
                if (!conflictIds.contains(expert.getId())) {
                    candidates.add(expert);
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // 负载均衡：选择当前分配稿件数最少的专家
        ZhuanjiaEntity best = null;
        int minCount = Integer.MAX_VALUE;
        for (ZhuanjiaEntity expert : candidates) {
            int count = baseMapper.selectCount(
                    new EntityWrapper<GaojianEntity>()
                            .eq("zhuanjia_id", expert.getId())
                            .in("gaojian_status_types", Arrays.asList(2, 3))
            );
            if (count < minCount) {
                minCount = count;
                best = expert;
            }
        }

        if (best != null) {
            // 更新稿件分配
            gaojian.setZhuanjiaId(best.getId());
            gaojian.setGaojianStatusTypes(2); // 审稿中
            baseMapper.updateById(gaojian);
            return best.getId();
        }

        return null;
    }

}
