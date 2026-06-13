package com.controller;


import java.text.SimpleDateFormat;
import com.alibaba.fastjson.JSONObject;
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
import com.dao.GaojianDao;
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

    /** 状态常量 */
    private static final int STATUS_PENDING    = 1; // 待审
    private static final int STATUS_REVIEWING  = 2; // 审稿中
    private static final int STATUS_REVISION   = 3; // 修回
    private static final int STATUS_ACCEPTED   = 4; // 录用
    private static final int STATUS_REJECTED   = 5; // 退稿

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
    @Autowired
    private GaojianDao gaojianDao;


    /**
    * 后端列表 —— 数据隔离：作者只看自己的，专家只看分配给自己的
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
    * 投稿 —— 仅作者可用，初始状态为"待审"
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
        gaojian.setGaojianYesnoTypes(STATUS_PENDING);
        gaojian.setZhuanjiaId(null);
        gaojian.setGaojianShenheContent(null);
        gaojian.setGaojianYesnoText(null);
        gaojian.setGaojianFileHistory(null);
        gaojian.setInsertTime(new Date());
        gaojian.setCreateTime(new Date());
        gaojianService.insert(gaojian);
        return R.ok();
    }

    /**
    * 分配专家 —— 仅管理员可用
    * 参数: gaojianId(必填), zhuanjiaId(选填，不填则按学科自动匹配)
    */
    @RequestMapping("/assign")
    public R assign(@RequestBody JSONObject jsonObject, HttpServletRequest request){
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(!"管理员".equals(role))
            return R.error(511,"只有管理员可以分配专家");

        Integer gaojianId  = jsonObject.getInteger("gaojianId");
        Integer zhuanjiaId = jsonObject.getInteger("zhuanjiaId");
        if(gaojianId == null)
            return R.error(511,"稿件ID不能为空");

        GaojianEntity gaojian = gaojianService.selectById(gaojianId);
        if(gaojian == null)
            return R.error(511,"稿件不存在");
        if(gaojian.getGaojianYesnoTypes() != STATUS_PENDING)
            return R.error(511,"当前状态不允许此操作，只有待审状态的稿件才能分配专家");

        // 自动匹配：若未指定专家，按学科类型查找
        if(zhuanjiaId == null){
            List<ZhuanjiaEntity> candidates = gaojianDao.selectMatchingExperts(
                gaojian.getGaojianTypes(), gaojian.getZuozheId());
            if(candidates == null || candidates.isEmpty())
                return R.error(511,"未找到匹配该学科的专家，请手动指定");
            zhuanjiaId = candidates.get(0).getId();
        }

        ZhuanjiaEntity zhuanjia = zhuanjiaService.selectById(zhuanjiaId);
        if(zhuanjia == null)
            return R.error(511,"指定的专家不存在");

        // 利益冲突检查：比较身份证号
        ZuozheEntity zuozhe = zuozheService.selectById(gaojian.getZuozheId());
        if(zuozhe != null && zhuanjia.getZhuanjiaIdNumber() != null
            && zuozhe.getZuozheIdNumber() != null
            && zhuanjia.getZhuanjiaIdNumber().equals(zuozhe.getZuozheIdNumber())){
            return R.error(511,"该专家与作者存在利益冲突（身份证号一致），不能分配");
        }

        gaojian.setZhuanjiaId(zhuanjiaId);
        gaojian.setGaojianYesnoTypes(STATUS_REVIEWING);
        gaojianService.updateById(gaojian);
        return R.ok().put("data","已分配专家：" + zhuanjia.getZhuanjiaName());
    }

    /**
    * 专家审稿 —— 仅专家可用，写审稿意见（不改变状态）
    * 参数: gaojianId(必填), reviewText(必填)
    */
    @RequestMapping("/review")
    public R review(@RequestBody JSONObject jsonObject, HttpServletRequest request){
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(!"专家".equals(role))
            return R.error(511,"只有专家可以审稿");

        Integer gaojianId  = jsonObject.getInteger("gaojianId");
        String  reviewText = jsonObject.getString("reviewText");
        if(gaojianId == null)
            return R.error(511,"稿件ID不能为空");
        if(StringUtils.isBlank(reviewText))
            return R.error(511,"审稿意见不能为空");

        GaojianEntity gaojian = gaojianService.selectById(gaojianId);
        if(gaojian == null)
            return R.error(511,"稿件不存在");
        if(gaojian.getGaojianYesnoTypes() != STATUS_REVIEWING)
            return R.error(511,"当前状态不允许此操作，只有审稿中的稿件才能提交意见");

        // 校验：当前专家必须是分配的审稿人
        Integer currentUserId = Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId")));
        if(gaojian.getZhuanjiaId() == null || !gaojian.getZhuanjiaId().equals(currentUserId))
            return R.error(511,"您不是该稿件的审稿专家");

        // 获取专家姓名
        ZhuanjiaEntity zhuanjia = zhuanjiaService.selectById(currentUserId);
        String expertName = (zhuanjia != null && zhuanjia.getZhuanjiaName() != null)
                ? zhuanjia.getZhuanjiaName() : "专家";

        // 追加意见到意见链
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String entry = "[专家-" + expertName + " " + sdf.format(new Date()) + "] " + reviewText;
        String existingText = gaojian.getGaojianYesnoText();
        if(StringUtils.isNotBlank(existingText)){
            gaojian.setGaojianYesnoText(existingText + "\n" + entry);
        } else {
            gaojian.setGaojianYesnoText(entry);
        }

        gaojianService.updateById(gaojian);
        return R.ok();
    }

    /**
    * 管理员终裁 —— 仅管理员可用
    * 参数: gaojianId(必填), decision(必填: 3=修回/4=录用/5=退稿), comment(选填)
    */
    @RequestMapping("/finalize")
    public R finalizeReview(@RequestBody JSONObject jsonObject, HttpServletRequest request){
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(!"管理员".equals(role))
            return R.error(511,"只有管理员可以终裁");

        Integer gaojianId = jsonObject.getInteger("gaojianId");
        Integer decision  = jsonObject.getInteger("decision");
        String  comment   = jsonObject.getString("comment");
        if(gaojianId == null)
            return R.error(511,"稿件ID不能为空");
        if(decision == null)
            return R.error(511,"终裁决定不能为空");
        if(decision != STATUS_REVISION && decision != STATUS_ACCEPTED && decision != STATUS_REJECTED)
            return R.error(511,"终裁决定只能是 3(修回)、4(录用)、5(退稿)");

        GaojianEntity gaojian = gaojianService.selectById(gaojianId);
        if(gaojian == null)
            return R.error(511,"稿件不存在");
        if(gaojian.getGaojianYesnoTypes() != STATUS_REVIEWING)
            return R.error(511,"当前状态不允许此操作，只有审稿中的稿件才能终裁");

        // 追加终裁记录到意见链
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String decisionName;
        if(decision == STATUS_REVISION) decisionName = "修回";
        else if(decision == STATUS_ACCEPTED) decisionName = "录用";
        else decisionName = "退稿";

        String entry = "[管理员终裁 " + sdf.format(new Date()) + "] " + decisionName;
        if(StringUtils.isNotBlank(comment)){
            entry += "：" + comment;
        }
        String existingText = gaojian.getGaojianYesnoText();
        if(StringUtils.isNotBlank(existingText)){
            gaojian.setGaojianYesnoText(existingText + "\n" + entry);
        } else {
            gaojian.setGaojianYesnoText(entry);
        }

        gaojian.setGaojianYesnoTypes(decision);
        gaojian.setGaojianShenheContent(comment);
        gaojianService.updateById(gaojian);
        return R.ok().put("data","终裁完成：" + decisionName);
    }

    /**
    * 作者修回重提 —— 仅作者可用，仅"修回"状态可操作
    * 参数: gaojianId(必填), gaojianFile(必填-新版本文件), resubmitNote(选填-修回说明)
    */
    @RequestMapping("/resubmit")
    public R resubmit(@RequestBody JSONObject jsonObject, HttpServletRequest request){
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(!"作者".equals(role))
            return R.error(511,"只有作者可以修回重提");

        Integer gaojianId    = jsonObject.getInteger("gaojianId");
        String  newFile      = jsonObject.getString("gaojianFile");
        String  resubmitNote = jsonObject.getString("resubmitNote");
        if(gaojianId == null)
            return R.error(511,"稿件ID不能为空");
        if(StringUtils.isBlank(newFile))
            return R.error(511,"修回稿件文件不能为空");

        GaojianEntity gaojian = gaojianService.selectById(gaojianId);
        if(gaojian == null)
            return R.error(511,"稿件不存在");
        if(gaojian.getGaojianYesnoTypes() != STATUS_REVISION)
            return R.error(511,"当前状态不允许此操作，只有修回状态的稿件才能重提");

        // 校验：当前用户必须是原作者
        Integer currentUserId = Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId")));
        if(!gaojian.getZuozheId().equals(currentUserId))
            return R.error(511,"您不是该稿件的作者");

        // 保留旧文件到历史版本
        String oldFile = gaojian.getGaojianFile();
        if(StringUtils.isNotBlank(oldFile)){
            String history = gaojian.getGaojianFileHistory();
            if(StringUtils.isNotBlank(history)){
                gaojian.setGaojianFileHistory(history + ";" + oldFile);
            } else {
                gaojian.setGaojianFileHistory(oldFile);
            }
        }

        // 追加修回记录到意见链
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String entry = "[作者修回 " + sdf.format(new Date()) + "]";
        if(StringUtils.isNotBlank(resubmitNote)){
            entry += " " + resubmitNote;
        }
        String existingText = gaojian.getGaojianYesnoText();
        if(StringUtils.isNotBlank(existingText)){
            gaojian.setGaojianYesnoText(existingText + "\n" + entry);
        } else {
            gaojian.setGaojianYesnoText(entry);
        }

        gaojian.setGaojianFile(newFile);
        gaojian.setGaojianYesnoTypes(STATUS_REVIEWING);
        gaojian.setInsertTime(new Date());
        gaojianService.updateById(gaojian);
        return R.ok();
    }

    /**
    * 通用更新 —— 仅管理员可用，且不能通过此接口修改审稿状态
    */
    @RequestMapping("/update")
    public R update(@RequestBody GaojianEntity gaojian, HttpServletRequest request){
        logger.debug("update方法:,,Controller:{},,gaojian:{}",this.getClass().getName(),gaojian.toString());

        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isEmpty(role))
            return R.error(511,"权限为空");
        if(!"管理员".equals(role))
            return R.error(511,"只有管理员可以使用通用更新");

        // 禁止通过通用更新修改审稿状态，必须走 /assign、/finalize 专用接口
        gaojian.setGaojianYesnoTypes(null);
        gaojian.setZhuanjiaId(null);

        if("".equals(gaojian.getGaojianFile()) || "null".equals(gaojian.getGaojianFile())){
                gaojian.setGaojianFile(null);
        }
        gaojianService.updateById(gaojian);//根据id更新
        return R.ok();
    }



    /**
    * 删除 —— 仅管理员可用
    */
    @RequestMapping("/delete")
    public R delete(@RequestBody Integer[] ids, HttpServletRequest request){
        logger.debug("delete:,,Controller:{},,ids:{}",this.getClass().getName(),ids.toString());
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(!"管理员".equals(role))
            return R.error(511,"只有管理员可以删除稿件");
        gaojianService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }


}
