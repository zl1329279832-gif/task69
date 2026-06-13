package com.controller;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.entity.GaojianEntity;
import com.entity.ZhuanjiaEntity;
import com.service.DictionaryService;
import com.service.GaojianService;
import com.service.ZhuanjiaService;
import com.service.ZuozheService;
import com.utils.R;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 审稿流程完整测试
 * 覆盖：投稿→分配→审稿→修回→终裁，以及非法跳转拒绝和数据隔离
 */
public class GaojianReviewFlowTest {

    @InjectMocks
    private GaojianController controller;

    @Mock
    private GaojianService gaojianService;

    @Mock
    private ZhuanjiaService zhuanjiaService;

    @Mock
    private ZuozheService zuozheService;

    @Mock
    private DictionaryService dictionaryService;

    private MockHttpServletRequest request;

    // 模拟用户ID
    private static final Integer AUTHOR_ID = 10;
    private static final Integer EXPERT_ID = 20;
    private static final Integer OTHER_EXPERT_ID = 21;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        request = new MockHttpServletRequest();
    }

    // ========== 辅助方法 ==========

    private void setRole(String role) {
        request.getSession().setAttribute("role", role);
    }

    private void setUserId(Integer userId) {
        request.getSession().setAttribute("userId", userId);
    }

    private GaojianEntity buildGaojian(Integer id, Integer status) {
        GaojianEntity g = new GaojianEntity();
        g.setId(id);
        g.setZuozheId(AUTHOR_ID);
        g.setGaojianName("测试论文");
        g.setGaojianTypes(1);
        g.setGaojianContent("摘要");
        g.setGaojianFile("/upload/test.doc");
        g.setGaojianStatusTypes(status);
        g.setGaojianYesnoText("[]");
        g.setGaojianFileHistory("[]");
        return g;
    }

    private int getCode(R r) {
        return (int) r.get("code");
    }

    // ========== 1. 作者投稿 ==========

    @Test
    public void testAuthorSubmit_success() {
        setRole("作者");
        setUserId(AUTHOR_ID);

        GaojianEntity input = new GaojianEntity();
        input.setGaojianName("测试论文");
        input.setGaojianTypes(1);
        input.setGaojianContent("摘要");
        input.setGaojianFile("/upload/test.doc");

        when(gaojianService.insert(any(GaojianEntity.class))).thenReturn(true);

        R result = controller.save(input, request);

        assertEquals(0, getCode(result));
        assertEquals(Integer.valueOf(GaojianController.STATUS_DAI_SHEN), input.getGaojianStatusTypes());
        assertEquals(AUTHOR_ID, input.getZuozheId());
        assertEquals("[]", input.getGaojianFileHistory());
        assertEquals("[]", input.getGaojianYesnoText());
        verify(gaojianService).insert(input);
    }

    @Test
    public void testAuthorSubmit_notAuthor_rejected() {
        setRole("专家");
        setUserId(EXPERT_ID);

        GaojianEntity input = new GaojianEntity();
        input.setGaojianName("测试论文");

        R result = controller.save(input, request);
        assertEquals(511, getCode(result));
    }

    // ========== 2. 管理员分配专家 ==========

    @Test
    public void testAdminAssign_success() {
        setRole("管理员");

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_DAI_SHEN);
        when(gaojianService.selectById(1)).thenReturn(original);

        ZhuanjiaEntity expert = new ZhuanjiaEntity();
        expert.setId(EXPERT_ID);
        when(zhuanjiaService.selectById(EXPERT_ID)).thenReturn(expert);

        GaojianEntity input = new GaojianEntity();
        input.setId(1);
        input.setZhuanjiaId(EXPERT_ID);

        R result = controller.assign(input, request);

        assertEquals(0, getCode(result));
        assertEquals(Integer.valueOf(GaojianController.STATUS_SHEN_GAO_ZHONG), original.getGaojianStatusTypes());
        assertEquals(EXPERT_ID, original.getZhuanjiaId());
        verify(gaojianService).updateById(original);
    }

    @Test
    public void testAdminAssign_notAdmin_rejected() {
        setRole("作者");
        setUserId(AUTHOR_ID);

        GaojianEntity input = new GaojianEntity();
        input.setId(1);
        input.setZhuanjiaId(EXPERT_ID);

        R result = controller.assign(input, request);
        assertEquals(511, getCode(result));
    }

    @Test
    public void testAdminAssign_wrongStatus_rejected() {
        setRole("管理员");

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_SHEN_GAO_ZHONG);
        when(gaojianService.selectById(1)).thenReturn(original);

        GaojianEntity input = new GaojianEntity();
        input.setId(1);
        input.setZhuanjiaId(EXPERT_ID);

        R result = controller.assign(input, request);
        assertEquals(511, getCode(result));
        assertTrue(result.get("msg").toString().contains("不允许"));
    }

    // ========== 3. 专家审稿 ==========

    @Test
    public void testExpertReview_xiuhui_success() {
        setRole("专家");
        setUserId(EXPERT_ID);

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_SHEN_GAO_ZHONG);
        original.setZhuanjiaId(EXPERT_ID);
        when(gaojianService.selectById(1)).thenReturn(original);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", 1);
        params.put("gaojianShenheContent", "建议修改引言部分");
        params.put("reviewConclusion", GaojianController.STATUS_XIU_HUI);

        R result = controller.review(params, request);

        assertEquals(0, getCode(result));
        assertEquals(Integer.valueOf(GaojianController.STATUS_XIU_HUI), original.getGaojianStatusTypes());
        assertNotNull(original.getGaojianYesnoText());
        assertTrue(original.getGaojianYesnoText().contains("修回"));
        verify(gaojianService).updateById(original);
    }

    @Test
    public void testExpertReview_luyong_success() {
        setRole("专家");
        setUserId(EXPERT_ID);

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_SHEN_GAO_ZHONG);
        original.setZhuanjiaId(EXPERT_ID);
        when(gaojianService.selectById(1)).thenReturn(original);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", 1);
        params.put("gaojianShenheContent", "论文质量优秀");
        params.put("reviewConclusion", GaojianController.STATUS_LU_YONG);

        R result = controller.review(params, request);

        assertEquals(0, getCode(result));
        assertEquals(Integer.valueOf(GaojianController.STATUS_LU_YONG), original.getGaojianStatusTypes());
    }

    @Test
    public void testExpertReview_notAssigned_rejected() {
        setRole("专家");
        setUserId(OTHER_EXPERT_ID); // 不是分配的专家

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_SHEN_GAO_ZHONG);
        original.setZhuanjiaId(EXPERT_ID); // 分配的专家是EXPERT_ID
        when(gaojianService.selectById(1)).thenReturn(original);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", 1);
        params.put("reviewConclusion", GaojianController.STATUS_XIU_HUI);

        R result = controller.review(params, request);
        assertEquals(511, getCode(result));
        assertTrue(result.get("msg").toString().contains("不是"));
    }

    @Test
    public void testExpertReview_wrongStatus_rejected() {
        setRole("专家");
        setUserId(EXPERT_ID);

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_DAI_SHEN); // 待审状态
        original.setZhuanjiaId(EXPERT_ID);
        when(gaojianService.selectById(1)).thenReturn(original);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", 1);
        params.put("reviewConclusion", GaojianController.STATUS_XIU_HUI);

        R result = controller.review(params, request);
        assertEquals(511, getCode(result));
    }

    @Test
    public void testExpertReview_illegalConclusion_rejected() {
        setRole("专家");
        setUserId(EXPERT_ID);

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_SHEN_GAO_ZHONG);
        original.setZhuanjiaId(EXPERT_ID);
        when(gaojianService.selectById(1)).thenReturn(original);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", 1);
        params.put("reviewConclusion", 1); // 非法结论

        R result = controller.review(params, request);
        assertEquals(511, getCode(result));
    }

    // ========== 4. 作者修回重提 ==========

    @Test
    public void testAuthorRevision_success() {
        setRole("作者");
        setUserId(AUTHOR_ID);

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_XIU_HUI);
        original.setGaojianFile("/upload/old.doc");
        original.setGaojianFileHistory("[]");
        when(gaojianService.selectById(1)).thenReturn(original);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", 1);
        params.put("gaojianFile", "/upload/new.doc");
        params.put("revisionNote", "已根据审稿意见修改");

        R result = controller.revision(params, request);

        assertEquals(0, getCode(result));
        assertEquals(Integer.valueOf(GaojianController.STATUS_DAI_SHEN), original.getGaojianStatusTypes());
        assertEquals("/upload/new.doc", original.getGaojianFile());
        // 验证旧文件被保存到历史
        assertTrue(original.getGaojianFileHistory().contains("/upload/old.doc"));
        // 验证意见链追加了作者记录
        assertTrue(original.getGaojianYesnoText().contains("作者"));
        verify(gaojianService).updateById(original);
    }

    @Test
    public void testAuthorRevision_wrongStatus_rejected() {
        setRole("作者");
        setUserId(AUTHOR_ID);

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_LU_YONG); // 已录用
        when(gaojianService.selectById(1)).thenReturn(original);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", 1);
        params.put("gaojianFile", "/upload/new.doc");

        R result = controller.revision(params, request);
        assertEquals(511, getCode(result));
    }

    @Test
    public void testAuthorRevision_notAuthor_rejected() {
        setRole("作者");
        setUserId(999); // 不是作者

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_XIU_HUI);
        when(gaojianService.selectById(1)).thenReturn(original);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", 1);
        params.put("gaojianFile", "/upload/new.doc");

        R result = controller.revision(params, request);
        assertEquals(511, getCode(result));
    }

    // ========== 5. 管理员终裁 ==========

    @Test
    public void testAdminFinalDecision_luyong() {
        setRole("管理员");

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_XIU_HUI);
        when(gaojianService.selectById(1)).thenReturn(original);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", 1);
        params.put("decision", GaojianController.STATUS_LU_YONG);
        params.put("decisionContent", "经终审，予以录用");

        R result = controller.finalDecision(params, request);

        assertEquals(0, getCode(result));
        assertEquals(Integer.valueOf(GaojianController.STATUS_LU_YONG), original.getGaojianStatusTypes());
        assertTrue(original.getGaojianYesnoText().contains("管理员"));
    }

    @Test
    public void testAdminFinalDecision_tuigao() {
        setRole("管理员");

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_XIU_HUI);
        when(gaojianService.selectById(1)).thenReturn(original);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", 1);
        params.put("decision", GaojianController.STATUS_TUI_GAO);
        params.put("decisionContent", "修改后仍不符合要求");

        R result = controller.finalDecision(params, request);

        assertEquals(0, getCode(result));
        assertEquals(Integer.valueOf(GaojianController.STATUS_TUI_GAO), original.getGaojianStatusTypes());
    }

    @Test
    public void testAdminFinalDecision_illegalDecision_rejected() {
        setRole("管理员");

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_XIU_HUI);
        when(gaojianService.selectById(1)).thenReturn(original);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", 1);
        params.put("decision", 1); // 非法终裁结论

        R result = controller.finalDecision(params, request);
        assertEquals(511, getCode(result));
    }

    // ========== 6. 非法跳转拒绝 ==========

    @Test
    public void testUpdate_expertBypass_rejected() {
        setRole("专家");
        setUserId(EXPERT_ID);

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_SHEN_GAO_ZHONG);
        when(gaojianService.selectById(1)).thenReturn(original);

        GaojianEntity input = new GaojianEntity();
        input.setId(1);
        input.setGaojianName("修改名称");

        R result = controller.update(input, request);
        assertEquals(511, getCode(result));
        assertTrue(result.get("msg").toString().contains("审稿接口"));
    }

    @Test
    public void testUpdate_authorCannotChangeStatus() {
        setRole("作者");
        setUserId(AUTHOR_ID);

        GaojianEntity original = buildGaojian(1, GaojianController.STATUS_LU_YONG);
        when(gaojianService.selectById(1)).thenReturn(original);

        GaojianEntity input = new GaojianEntity();
        input.setId(1);
        input.setGaojianStatusTypes(GaojianController.STATUS_DAI_SHEN); // 试图改状态
        input.setGaojianName("修改名称");

        when(gaojianService.updateById(any(GaojianEntity.class))).thenReturn(true);

        R result = controller.update(input, request);

        assertEquals(0, getCode(result));
        // 验证状态被置null，不会通过updateById更新
        assertNull(input.getGaojianStatusTypes());
    }

    @Test
    public void testDelete_nonAdmin_rejected() {
        setRole("专家");
        setUserId(EXPERT_ID);

        R result = controller.delete(new Integer[]{1}, request);
        assertEquals(511, getCode(result));
    }

    // ========== 7. 完整流程测试 ==========

    @Test
    public void testFullWorkflow() {
        // Step 1: 作者投稿
        setRole("作者");
        setUserId(AUTHOR_ID);
        GaojianEntity gaojian = new GaojianEntity();
        gaojian.setGaojianName("完整流程测试论文");
        gaojian.setGaojianTypes(1);
        gaojian.setGaojianFile("/upload/v1.doc");
        when(gaojianService.insert(any(GaojianEntity.class))).thenReturn(true);
        R r1 = controller.save(gaojian, request);
        assertEquals(0, getCode(r1));
        assertEquals(Integer.valueOf(1), gaojian.getGaojianStatusTypes());

        // 模拟DB分配ID
        gaojian.setId(1);
        when(gaojianService.selectById(1)).thenReturn(gaojian);

        // Step 2: 管理员分配专家
        setRole("管理员");
        request.getSession().setAttribute("userId", 1);
        GaojianEntity assignInput = new GaojianEntity();
        assignInput.setId(1);
        assignInput.setZhuanjiaId(EXPERT_ID);
        ZhuanjiaEntity expert = new ZhuanjiaEntity();
        expert.setId(EXPERT_ID);
        when(zhuanjiaService.selectById(EXPERT_ID)).thenReturn(expert);
        R r2 = controller.assign(assignInput, request);
        assertEquals(0, getCode(r2));
        assertEquals(Integer.valueOf(2), gaojian.getGaojianStatusTypes());

        // Step 3: 专家审稿 → 修回
        setRole("专家");
        setUserId(EXPERT_ID);
        Map<String, Object> reviewParams = new HashMap<String, Object>();
        reviewParams.put("id", 1);
        reviewParams.put("gaojianShenheContent", "需修改第三章");
        reviewParams.put("reviewConclusion", GaojianController.STATUS_XIU_HUI);
        R r3 = controller.review(reviewParams, request);
        assertEquals(0, getCode(r3));
        assertEquals(Integer.valueOf(3), gaojian.getGaojianStatusTypes());

        // Step 4: 作者修回重提
        setRole("作者");
        setUserId(AUTHOR_ID);
        gaojian.setGaojianFile("/upload/v1.doc"); // 模拟旧文件
        gaojian.setGaojianFileHistory("[]");
        Map<String, Object> revisionParams = new HashMap<String, Object>();
        revisionParams.put("id", 1);
        revisionParams.put("gaojianFile", "/upload/v2.doc");
        revisionParams.put("revisionNote", "已修改第三章");
        R r4 = controller.revision(revisionParams, request);
        assertEquals(0, getCode(r4));
        assertEquals(Integer.valueOf(1), gaojian.getGaojianStatusTypes());
        assertEquals("/upload/v2.doc", gaojian.getGaojianFile());
        assertTrue(gaojian.getGaojianFileHistory().contains("/upload/v1.doc"));

        // Step 5: 管理员再次分配（自动或手动）
        setRole("管理员");
        GaojianEntity assignInput2 = new GaojianEntity();
        assignInput2.setId(1);
        assignInput2.setZhuanjiaId(EXPERT_ID);
        R r5 = controller.assign(assignInput2, request);
        assertEquals(0, getCode(r5));
        assertEquals(Integer.valueOf(2), gaojian.getGaojianStatusTypes());

        // Step 6: 专家审稿 → 录用
        setRole("专家");
        setUserId(EXPERT_ID);
        Map<String, Object> reviewParams2 = new HashMap<String, Object>();
        reviewParams2.put("id", 1);
        reviewParams2.put("gaojianShenheContent", "修改后质量达标");
        reviewParams2.put("reviewConclusion", GaojianController.STATUS_LU_YONG);
        R r6 = controller.review(reviewParams2, request);
        assertEquals(0, getCode(r6));
        assertEquals(Integer.valueOf(4), gaojian.getGaojianStatusTypes());

        // Step 7: 验证终态不可变 - 作者不能修回已录用稿
        setRole("作者");
        setUserId(AUTHOR_ID);
        Map<String, Object> illegalRevision = new HashMap<String, Object>();
        illegalRevision.put("id", 1);
        illegalRevision.put("gaojianFile", "/upload/v3.doc");
        R r7 = controller.revision(illegalRevision, request);
        assertEquals(511, getCode(r7));
    }
}
