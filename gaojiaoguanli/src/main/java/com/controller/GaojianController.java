package com.controller;


import java.text.SimpleDateFormat;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;
import java.util.*;
import org.springframework.beans.BeanUtils;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.ContextLoader;
import javax.servlet.ServletContext;
import com.service.TokenService;
import com.utils.StringUtil;
import java.lang.reflect.InvocationTargetException;

import com.service.DictionaryService;
import org.apache.commons.lang3.StringUtils;
import com.annotation.IgnoreAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;

import com.entity.GaojianEntity;

import com.service.GaojianService;
import com.entity.view.GaojianView;
import com.service.ZhuanjiaService;
import com.entity.ZhuanjiaEntity;
import com.service.ZuozheService;
import com.entity.ZuozheEntity;
import com.utils.PageUtils;
import com.utils.R;

/**
 * 稿件
 * 后端接口
 * @author
 * @email
*/
@RestController
@Controller
@RequestMapping("/gaojian")
public class GaojianController {
    private static final Logger logger = LoggerFactory.getLogger(GaojianController.class);

    // ========== 状态机常量 ==========
    /** 待审 */
    public static final int STATUS_DAI_SHEN = 1;
    /** 审稿中 */
    public static final int STATUS_SHEN_GAO_ZHONG = 2;
    /** 修回 */
    public static final int STATUS_XIU_HUI = 3;
    /** 录用 */
    public static final int STATUS_LU_YONG = 4;
    /** 退稿 */
    public static final int STATUS_TUI_GAO = 5;

    @Autowired
    private GaojianService gaojianService;


    @Autowired
    private TokenService tokenService;
    @Autowired
    private DictionaryService dictionaryService;



    //级联表service
    @Autowired
    private ZhuanjiaService zhuanjiaService;
    @Autowired
    private ZuozheService zuozheService;


    /**
    * 后端列表
    */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("page方法:,,Controller:{},,params:{}",this.getClass().getName(),JSONObject.toJSONString(params));
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isEmpty(role))
            return R.error(511,"权限为空");
        else if("作者".equals(role))
            params.put("zuozheId",request.getSession().getAttribute("userId"));
        else if("专家".equals(role))
            params.put("zhuanjiaId",request.getSession().getAttribute("userId"));
        params.put("orderBy","id");
        PageUtils page = gaojianService.queryPage(params);

        //字典表数据转换
        List<GaojianView> list =(List<GaojianView>)page.getList();
        for(GaojianView c:list){
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(c);
        }
        return R.ok().put("data", page);
    }

    /**
    * 后端详情
    */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        logger.debug("info方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        GaojianEntity gaojian = gaojianService.selectById(id);
        if(gaojian !=null){
            //entity转view
            GaojianView view = new GaojianView();
            BeanUtils.copyProperties( gaojian , view );//把实体数据重构到view中

            //级联表
            ZhuanjiaEntity zhuanjia = zhuanjiaService.selectById(gaojian.getZhuanjiaId());
            if(zhuanjia != null){
                BeanUtils.copyProperties( zhuanjia , view ,new String[]{ "id", "createDate"});//把级联的数据添加到view中,并排除id和创建时间字段
                view.setZhuanjiaId(zhuanjia.getId());
            }
            //级联表
            ZuozheEntity zuozhe = zuozheService.selectById(gaojian.getZuozheId());
            if(zuozhe != null){
                BeanUtils.copyProperties( zuozhe , view ,new String[]{ "id", "createDate"});//把级联的数据添加到view中,并排除id和创建时间字段
                view.setZuozheId(zuozhe.getId());
            }
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(view);
            return R.ok().put("data", view);
        }else {
            return R.error(511,"查不到数据");
        }

    }

    /**
    * 作者投稿（仅作者可用）
    */
    @RequestMapping("/save")
    public R save(@RequestBody GaojianEntity gaojian, HttpServletRequest request){
        logger.debug("save方法:,,Controller:{},,gaojian:{}",this.getClass().getName(),gaojian.toString());

        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isEmpty(role))
            return R.error(511,"权限为空");
        if(!"作者".equals(role))
            return R.error(511,"只有作者可以投稿");

        gaojian.setZuozheId(Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId"))));
        gaojian.setGaojianYesnoTypes(1);
        gaojian.setGaojianStatusTypes(STATUS_DAI_SHEN);
        gaojian.setInsertTime(new Date());
        gaojian.setCreateTime(new Date());
        // 初始化文件历史和意见链为空数组
        gaojian.setGaojianFileHistory("[]");
        gaojian.setGaojianYesnoText("[]");
        gaojianService.insert(gaojian);
        return R.ok();
    }

    /**
    * 管理员修改稿件基本信息（不改变流程状态）
    */
    @RequestMapping("/update")
    public R update(@RequestBody GaojianEntity gaojian, HttpServletRequest request){
        logger.debug("update方法:,,Controller:{},,gaojian:{}",this.getClass().getName(),gaojian.toString());

        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isEmpty(role))
            return R.error(511,"权限为空");

        // 查询原始数据
        GaojianEntity original = gaojianService.selectById(gaojian.getId());
        if(original == null)
            return R.error(511,"稿件不存在");

        if("作者".equals(role)){
            // 作者不能通过通用update改状态
            if(!original.getZuozheId().equals(Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId")))))
                return R.error(511,"无权修改此稿件");
            // 作者只能修改名称、类型、介绍等基本字段，不能改状态
            gaojian.setGaojianStatusTypes(null);
            gaojian.setGaojianYesnoTypes(null);
            gaojian.setGaojianShenheContent(null);
            gaojian.setZhuanjiaId(null);
        } else if("专家".equals(role)){
            // 专家不能通过通用update改任何流程字段，必须走 /review 端点
            return R.error(511,"专家请使用审稿接口 /gaojian/review");
        }
        // 管理员可以修改基本字段，但不能通过此接口改状态
        gaojian.setGaojianStatusTypes(null);

        if("".equals(gaojian.getGaojianFile()) || "null".equals(gaojian.getGaojianFile())){
                gaojian.setGaojianFile(null);
        }
        gaojianService.updateById(gaojian);//根据id更新（null字段不会被更新）
        return R.ok();
    }


    /**
    * 删除（仅管理员）
    */
    @RequestMapping("/delete")
    public R delete(@RequestBody Integer[] ids, HttpServletRequest request){
        logger.debug("delete:,,Controller:{},,ids:{}",this.getClass().getName(),ids.toString());
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isEmpty(role))
            return R.error(511,"权限为空");
        if(!"管理员".equals(role))
            return R.error(511,"只有管理员可以删除稿件");
        gaojianService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }


    // ==================== 审稿流程专用端点 ====================

    /**
     * 管理员手动分配专家
     * 前提：稿件状态=待审(1)
     * 结果：状态→审稿中(2)
     */
    @RequestMapping("/assign")
    public R assign(@RequestBody GaojianEntity gaojian, HttpServletRequest request){
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isEmpty(role))
            return R.error(511,"权限为空");
        if(!"管理员".equals(role))
            return R.error(511,"只有管理员可以分配专家");

        GaojianEntity original = gaojianService.selectById(gaojian.getId());
        if(original == null)
            return R.error(511,"稿件不存在");
        if(original.getGaojianStatusTypes() == null || original.getGaojianStatusTypes() != STATUS_DAI_SHEN)
            return R.error(511,"当前状态不允许分配专家，当前状态：" + getStatusName(original.getGaojianStatusTypes()));

        if(gaojian.getZhuanjiaId() == null)
            return R.error(511,"请指定专家ID");

        // 校验专家存在
        ZhuanjiaEntity expert = zhuanjiaService.selectById(gaojian.getZhuanjiaId());
        if(expert == null)
            return R.error(511,"指定的专家不存在");

        original.setZhuanjiaId(gaojian.getZhuanjiaId());
        original.setGaojianStatusTypes(STATUS_SHEN_GAO_ZHONG);
        gaojianService.updateById(original);
        return R.ok("分配成功，稿件已进入审稿中");
    }

    /**
     * 管理员按学科自动匹配专家
     * 前提：稿件状态=待审(1)
     * 结果：状态→审稿中(2)
     */
    @RequestMapping("/autoAssign")
    public R autoAssign(@RequestBody GaojianEntity gaojian, HttpServletRequest request){
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isEmpty(role))
            return R.error(511,"权限为空");
        if(!"管理员".equals(role))
            return R.error(511,"只有管理员可以自动分配专家");

        GaojianEntity original = gaojianService.selectById(gaojian.getId());
        if(original == null)
            return R.error(511,"稿件不存在");
        if(original.getGaojianStatusTypes() == null || original.getGaojianStatusTypes() != STATUS_DAI_SHEN)
            return R.error(511,"当前状态不允许自动分配，当前状态：" + getStatusName(original.getGaojianStatusTypes()));

        Integer expertId = gaojianService.autoAssignExpert(gaojian.getId());
        if(expertId == null)
            return R.error(511,"未找到合适的专家，请手动分配");

        return R.ok("自动分配成功，专家ID：" + expertId);
    }

    /**
     * 专家提交审稿意见
     * 前提：稿件状态=审稿中(2) 且 当前专家是被分配的专家
     * 结果：状态→修回(3)/录用(4)/退稿(5)
     *
     * 请求体需包含：
     *   id - 稿件ID
     *   gaojianShenheContent - 审稿意见内容
     *   reviewConclusion - 审稿结论（3=修回, 4=录用, 5=退稿）
     */
    @RequestMapping("/review")
    public R review(@RequestBody Map<String, Object> params, HttpServletRequest request){
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isEmpty(role))
            return R.error(511,"权限为空");
        if(!"专家".equals(role))
            return R.error(511,"只有专家可以审稿");

        Integer userId = Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId")));
        Integer gaojianId = params.get("id") != null ? Integer.valueOf(String.valueOf(params.get("id"))) : null;
        if(gaojianId == null)
            return R.error(511,"稿件ID不能为空");

        GaojianEntity gaojian = gaojianService.selectById(gaojianId);
        if(gaojian == null)
            return R.error(511,"稿件不存在");

        // 校验当前专家是否被分配
        if(gaojian.getZhuanjiaId() == null || !gaojian.getZhuanjiaId().equals(userId))
            return R.error(511,"您不是该稿件的指定审稿专家");

        // 校验状态
        if(gaojian.getGaojianStatusTypes() == null || gaojian.getGaojianStatusTypes() != STATUS_SHEN_GAO_ZHONG)
            return R.error(511,"当前状态不允许审稿，当前状态：" + getStatusName(gaojian.getGaojianStatusTypes()));

        // 获取审稿结论
        Integer reviewConclusion = params.get("reviewConclusion") != null ? Integer.valueOf(String.valueOf(params.get("reviewConclusion"))) : null;
        if(reviewConclusion == null)
            return R.error(511,"审稿结论不能为空");
        if(reviewConclusion != STATUS_XIU_HUI && reviewConclusion != STATUS_LU_YONG && reviewConclusion != STATUS_TUI_GAO)
            return R.error(511,"审稿结论非法，只能是 3(修回)/4(录用)/5(退稿)");

        String shenheContent = params.get("gaojianShenheContent") != null ? String.valueOf(params.get("gaojianShenheContent")) : "";

        // 追加审稿意见到意见链
        JSONArray opinionChain;
        try {
            opinionChain = JSONArray.parseArray(gaojian.getGaojianYesnoText() != null ? gaojian.getGaojianYesnoText() : "[]");
        } catch (Exception e) {
            opinionChain = new JSONArray();
        }
        JSONObject opinion = new JSONObject();
        opinion.put("round", opinionChain.size() + 1);
        opinion.put("role", "专家");
        opinion.put("zhuanjiaId", userId);
        opinion.put("content", shenheContent);
        opinion.put("conclusion", getStatusName(reviewConclusion));
        opinion.put("conclusionCode", reviewConclusion);
        opinion.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        opinionChain.add(opinion);

        // 更新稿件
        gaojian.setGaojianShenheContent(shenheContent);
        gaojian.setGaojianYesnoTypes(reviewConclusion);
        gaojian.setGaojianStatusTypes(reviewConclusion);
        gaojian.setGaojianYesnoText(opinionChain.toJSONString());
        gaojianService.updateById(gaojian);

        return R.ok("审稿完成，结论：" + getStatusName(reviewConclusion));
    }

    /**
     * 作者修回重提
     * 前提：稿件状态=修回(3) 且 当前作者是稿件作者
     * 结果：状态→待审(1)，保留文件历史版本
     */
    @RequestMapping("/revision")
    public R revision(@RequestBody Map<String, Object> params, HttpServletRequest request){
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isEmpty(role))
            return R.error(511,"权限为空");
        if(!"作者".equals(role))
            return R.error(511,"只有作者可以修回重提");

        Integer userId = Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId")));
        Integer gaojianId = params.get("id") != null ? Integer.valueOf(String.valueOf(params.get("id"))) : null;
        if(gaojianId == null)
            return R.error(511,"稿件ID不能为空");

        GaojianEntity gaojian = gaojianService.selectById(gaojianId);
        if(gaojian == null)
            return R.error(511,"稿件不存在");

        // 校验当前作者
        if(!gaojian.getZuozheId().equals(userId))
            return R.error(511,"您不是该稿件的作者");

        // 校验状态
        if(gaojian.getGaojianStatusTypes() == null || gaojian.getGaojianStatusTypes() != STATUS_XIU_HUI)
            return R.error(511,"当前状态不允许修回，当前状态：" + getStatusName(gaojian.getGaojianStatusTypes()));

        String newFile = params.get("gaojianFile") != null ? String.valueOf(params.get("gaojianFile")) : null;
        if(newFile == null || newFile.isEmpty() || "null".equals(newFile))
            return R.error(511,"修回稿件必须上传新的稿件文件");

        // 保留文件历史版本
        JSONArray fileHistory;
        try {
            fileHistory = JSONArray.parseArray(gaojian.getGaojianFileHistory() != null ? gaojian.getGaojianFileHistory() : "[]");
        } catch (Exception e) {
            fileHistory = new JSONArray();
        }
        JSONObject historyEntry = new JSONObject();
        historyEntry.put("version", fileHistory.size() + 1);
        historyEntry.put("file", gaojian.getGaojianFile());
        historyEntry.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        fileHistory.add(historyEntry);

        // 追加作者修回记录到意见链
        JSONArray opinionChain;
        try {
            opinionChain = JSONArray.parseArray(gaojian.getGaojianYesnoText() != null ? gaojian.getGaojianYesnoText() : "[]");
        } catch (Exception e) {
            opinionChain = new JSONArray();
        }
        JSONObject authorRecord = new JSONObject();
        authorRecord.put("round", opinionChain.size() + 1);
        authorRecord.put("role", "作者");
        authorRecord.put("zuozheId", userId);
        String revisionNote = params.get("revisionNote") != null ? String.valueOf(params.get("revisionNote")) : "";
        authorRecord.put("content", revisionNote);
        authorRecord.put("conclusion", "修回重提");
        authorRecord.put("conclusionCode", STATUS_XIU_HUI);
        authorRecord.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        opinionChain.add(authorRecord);

        // 更新稿件
        gaojian.setGaojianFile(newFile);
        gaojian.setGaojianFileHistory(fileHistory.toJSONString());
        gaojian.setGaojianYesnoText(opinionChain.toJSONString());
        gaojian.setGaojianStatusTypes(STATUS_DAI_SHEN);
        gaojian.setInsertTime(new Date());
        gaojianService.updateById(gaojian);

        return R.ok("修回成功，稿件已重新进入待审");
    }

    /**
     * 管理员终裁
     * 前提：稿件状态=修回(3)（即经过至少一轮审稿后需要终裁）
     * 结果：状态→录用(4) 或 退稿(5)
     */
    @RequestMapping("/finalDecision")
    public R finalDecision(@RequestBody Map<String, Object> params, HttpServletRequest request){
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isEmpty(role))
            return R.error(511,"权限为空");
        if(!"管理员".equals(role))
            return R.error(511,"只有管理员可以终裁");

        Integer gaojianId = params.get("id") != null ? Integer.valueOf(String.valueOf(params.get("id"))) : null;
        if(gaojianId == null)
            return R.error(511,"稿件ID不能为空");

        GaojianEntity gaojian = gaojianService.selectById(gaojianId);
        if(gaojian == null)
            return R.error(511,"稿件不存在");

        // 校验状态
        if(gaojian.getGaojianStatusTypes() == null || gaojian.getGaojianStatusTypes() != STATUS_XIU_HUI)
            return R.error(511,"当前状态不允许终裁，当前状态：" + getStatusName(gaojian.getGaojianStatusTypes()));

        Integer decision = params.get("decision") != null ? Integer.valueOf(String.valueOf(params.get("decision"))) : null;
        if(decision == null)
            return R.error(511,"终裁结论不能为空");
        if(decision != STATUS_LU_YONG && decision != STATUS_TUI_GAO)
            return R.error(511,"终裁结论非法，只能是 4(录用) 或 5(退稿)");

        String decisionContent = params.get("decisionContent") != null ? String.valueOf(params.get("decisionContent")) : "";

        // 追加终裁记录到意见链
        JSONArray opinionChain;
        try {
            opinionChain = JSONArray.parseArray(gaojian.getGaojianYesnoText() != null ? gaojian.getGaojianYesnoText() : "[]");
        } catch (Exception e) {
            opinionChain = new JSONArray();
        }
        JSONObject adminRecord = new JSONObject();
        adminRecord.put("round", opinionChain.size() + 1);
        adminRecord.put("role", "管理员");
        adminRecord.put("content", decisionContent);
        adminRecord.put("conclusion", getStatusName(decision));
        adminRecord.put("conclusionCode", decision);
        adminRecord.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        opinionChain.add(adminRecord);

        // 更新稿件
        gaojian.setGaojianYesnoTypes(decision);
        gaojian.setGaojianStatusTypes(decision);
        gaojian.setGaojianYesnoText(opinionChain.toJSONString());
        gaojianService.updateById(gaojian);

        return R.ok("终裁完成，结论：" + getStatusName(decision));
    }


    // ==================== 辅助方法 ====================

    private String getStatusName(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case STATUS_DAI_SHEN: return "待审";
            case STATUS_SHEN_GAO_ZHONG: return "审稿中";
            case STATUS_XIU_HUI: return "修回";
            case STATUS_LU_YONG: return "录用";
            case STATUS_TUI_GAO: return "退稿";
            default: return "未知(" + status + ")";
        }
    }

}
