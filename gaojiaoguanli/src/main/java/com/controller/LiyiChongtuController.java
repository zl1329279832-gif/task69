package com.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.entity.LiyiChongtuEntity;
import com.service.LiyiChongtuService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

/**
 * 利益冲突管理
 * 后端接口（仅管理员可用）
 */
@RestController
@Controller
@RequestMapping("/liyiChongtu")
public class LiyiChongtuController {
    private static final Logger logger = LoggerFactory.getLogger(LiyiChongtuController.class);

    @Autowired
    private LiyiChongtuService liyiChongtuService;

    /**
     * 列表查询
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params, HttpServletRequest request) {
        logger.debug("page方法:,,Controller:{},,params:{}", this.getClass().getName(), JSONObject.toJSONString(params));
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if (StringUtil.isEmpty(role))
            return R.error(511, "权限为空");
        if (!"管理员".equals(role))
            return R.error(511, "只有管理员可以管理利益冲突");
        PageUtils page = liyiChongtuService.queryPage(params);
        return R.ok().put("data", page);
    }

    /**
     * 新增利益冲突关系
     */
    @RequestMapping("/save")
    public R save(@RequestBody LiyiChongtuEntity entity, HttpServletRequest request) {
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if (StringUtil.isEmpty(role))
            return R.error(511, "权限为空");
        if (!"管理员".equals(role))
            return R.error(511, "只有管理员可以添加利益冲突");

        if (entity.getZuozheId() == null || entity.getZhuanjiaId() == null)
            return R.error(511, "作者ID和专家ID不能为空");

        // 检查是否已存在
        LiyiChongtuEntity existing = liyiChongtuService.selectOne(
                new EntityWrapper<LiyiChongtuEntity>()
                        .eq("zuozhe_id", entity.getZuozheId())
                        .eq("zhuanjia_id", entity.getZhuanjiaId())
        );
        if (existing != null)
            return R.error(511, "该作者与专家的利益冲突关系已存在");

        entity.setCreateTime(new Date());
        liyiChongtuService.insert(entity);
        return R.ok();
    }

    /**
     * 删除利益冲突关系
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Integer[] ids, HttpServletRequest request) {
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if (StringUtil.isEmpty(role))
            return R.error(511, "权限为空");
        if (!"管理员".equals(role))
            return R.error(511, "只有管理员可以删除利益冲突");
        liyiChongtuService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }
}
