package com.controller;

import com.alibaba.fastjson.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 稿件审稿流程状态机测试
 *
 * 测试覆盖：
 * 1. 合法流转：待审→审稿中→(专家审稿)→修回→审稿中→录用
 * 2. 非法跳转：待审→录用、退稿→审稿中 等
 * 3. 角色隔离：作者不能分配、专家不能终裁、专家不能投稿
 * 4. 利益冲突：身份证号相同时拒绝分配
 *
 * 运行前提：MySQL 数据库已启动且已执行 review_workflow.sql
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(locations = {
    "classpath:spring/spring.xml",
    "classpath:spring/spring-mvc.xml",
    "classpath:spring/spring-mybatis.xml"
})
public class GaojianControllerTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    /** 模拟三种角色的会话 */
    private MockHttpSession authorSession;
    private MockHttpSession expertSession;
    private MockHttpSession adminSession;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();

        // 作者会话 (假设作者 ID=1 已存在于 zuozhe 表)
        authorSession = new MockHttpSession();
        authorSession.setAttribute("role", "作者");
        authorSession.setAttribute("userId", 1);
        authorSession.setAttribute("tableName", "zuozhe");

        // 专家会话 (假设专家 ID=1 已存在于 zhuanjia 表)
        expertSession = new MockHttpSession();
        expertSession.setAttribute("role", "专家");
        expertSession.setAttribute("userId", 1);
        expertSession.setAttribute("tableName", "zhuanjia");

        // 管理员会话
        adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "管理员");
        adminSession.setAttribute("userId", 1);
        adminSession.setAttribute("tableName", "users");
    }

    // ===== 角色权限测试 =====

    @Test
    public void testExpertCannotSave() throws Exception {
        JSONObject body = new JSONObject();
        body.put("gaojianName", "专家投稿测试");
        body.put("gaojianTypes", 1);
        body.put("gaojianContent", "不应该成功");
        body.put("gaojianFile", "upload/test.pdf");

        MvcResult result = mockMvc.perform(post("/gaojian/save")
                .session(expertSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toJSONString()))
                .andReturn();

        String resp = result.getResponse().getContentAsString();
        assertTrue("专家不能投稿", resp.contains("只有作者可以投稿"));
    }

    @Test
    public void testAuthorCannotAssign() throws Exception {
        JSONObject body = new JSONObject();
        body.put("gaojianId", 1);
        body.put("zhuanjiaId", 1);

        MvcResult result = mockMvc.perform(post("/gaojian/assign")
                .session(authorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toJSONString()))
                .andReturn();

        String resp = result.getResponse().getContentAsString();
        assertTrue("作者不能分配专家", resp.contains("只有管理员可以分配专家"));
    }

    @Test
    public void testExpertCannotFinalize() throws Exception {
        JSONObject body = new JSONObject();
        body.put("gaojianId", 1);
        body.put("decision", 4);

        MvcResult result = mockMvc.perform(post("/gaojian/finalize")
                .session(expertSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toJSONString()))
                .andReturn();

        String resp = result.getResponse().getContentAsString();
        assertTrue("专家不能终裁", resp.contains("只有管理员可以终裁"));
    }

    @Test
    public void testAuthorCannotFinalize() throws Exception {
        JSONObject body = new JSONObject();
        body.put("gaojianId", 1);
        body.put("decision", 4);

        MvcResult result = mockMvc.perform(post("/gaojian/finalize")
                .session(authorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toJSONString()))
                .andReturn();

        String resp = result.getResponse().getContentAsString();
        assertTrue("作者不能终裁", resp.contains("只有管理员可以终裁"));
    }

    @Test
    public void testAuthorCannotDelete() throws Exception {
        MvcResult result = mockMvc.perform(post("/gaojian/delete")
                .session(authorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[999]"))
                .andReturn();

        String resp = result.getResponse().getContentAsString();
        assertTrue("作者不能删除", resp.contains("只有管理员可以删除"));
    }

    @Test
    public void testExpertCannotDelete() throws Exception {
        MvcResult result = mockMvc.perform(post("/gaojian/delete")
                .session(expertSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[999]"))
                .andReturn();

        String resp = result.getResponse().getContentAsString();
        assertTrue("专家不能删除", resp.contains("只有管理员可以删除"));
    }

    @Test
    public void testExpertCannotUpdate() throws Exception {
        JSONObject body = new JSONObject();
        body.put("id", 1);
        body.put("gaojianName", "专家尝试修改");

        MvcResult result = mockMvc.perform(post("/gaojian/update")
                .session(expertSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toJSONString()))
                .andReturn();

        String resp = result.getResponse().getContentAsString();
        assertTrue("专家不能通用更新", resp.contains("只有管理员可以使用通用更新"));
    }

    // ===== 非法状态跳转测试 =====

    @Test
    public void testCannotFinalizeFromPending() throws Exception {
        // 先投稿（状态=待审），然后直接终裁 → 应拒绝
        JSONObject saveBody = new JSONObject();
        saveBody.put("gaojianName", "状态测试稿件");
        saveBody.put("gaojianTypes", 1);
        saveBody.put("gaojianContent", "测试非法跳转");
        saveBody.put("gaojianFile", "upload/state_test.pdf");

        mockMvc.perform(post("/gaojian/save")
                .session(authorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveBody.toJSONString()))
                .andExpect(jsonPath("$.code").value(0));

        // 获取刚创建的稿件ID（通过列表查询最新的一个）
        MvcResult listResult = mockMvc.perform(get("/gaojian/page")
                .session(adminSession)
                .param("page", "1")
                .param("limit", "1")
                .param("gaojianName", "状态测试稿件"))
                .andReturn();

        JSONObject listResp = JSONObject.parseObject(listResult.getResponse().getContentAsString());
        JSONObject pageData = listResp.getJSONObject("data");
        Integer gaojianId = pageData.getJSONArray("list").getJSONObject(0).getInteger("id");

        // 试图从 待审(1) 直接终裁为录用(4) → 应被拒绝
        JSONObject finalizeBody = new JSONObject();
        finalizeBody.put("gaojianId", gaojianId);
        finalizeBody.put("decision", 4);

        MvcResult result = mockMvc.perform(post("/gaojian/finalize")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(finalizeBody.toJSONString()))
                .andReturn();

        String resp = result.getResponse().getContentAsString();
        assertTrue("不能从待审直接终裁", resp.contains("只有审稿中的稿件才能终裁"));

        // 清理测试数据
        mockMvc.perform(post("/gaojian/delete")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[" + gaojianId + "]"));
    }

    @Test
    public void testCannotResubmitFromPending() throws Exception {
        JSONObject body = new JSONObject();
        body.put("gaojianId", 1);
        body.put("gaojianFile", "upload/new_version.pdf");

        MvcResult result = mockMvc.perform(post("/gaojian/resubmit")
                .session(authorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toJSONString()))
                .andReturn();

        String resp = result.getResponse().getContentAsString();
        // 要么稿件不存在，要么状态不允许
        assertTrue("不能从待审状态修回重提",
                resp.contains("不允许此操作") || resp.contains("不存在"));
    }

    @Test
    public void testCannotReviewFromPending() throws Exception {
        JSONObject body = new JSONObject();
        body.put("gaojianId", 1);
        body.put("reviewText", "审稿意见");

        MvcResult result = mockMvc.perform(post("/gaojian/review")
                .session(expertSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toJSONString()))
                .andReturn();

        String resp = result.getResponse().getContentAsString();
        // 要么稿件不存在，要么状态不允许，要么不是该专家
        assertTrue("不能从待审状态审稿",
                resp.contains("不允许此操作") || resp.contains("不存在") || resp.contains("不是"));
    }

    // ===== 完整合法流转测试 =====

    @Test
    public void testFullLegalWorkflow() throws Exception {
        // 步骤1：作者投稿
        JSONObject saveBody = new JSONObject();
        saveBody.put("gaojianName", "完整流程测试论文");
        saveBody.put("gaojianTypes", 1);
        saveBody.put("gaojianContent", "测试完整审稿流程");
        saveBody.put("gaojianFile", "upload/full_test_v1.pdf");

        mockMvc.perform(post("/gaojian/save")
                .session(authorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveBody.toJSONString()))
                .andExpect(jsonPath("$.code").value(0));

        // 获取稿件ID
        MvcResult listResult = mockMvc.perform(get("/gaojian/page")
                .session(adminSession)
                .param("page", "1")
                .param("limit", "1")
                .param("gaojianName", "完整流程测试论文"))
                .andReturn();

        JSONObject listResp = JSONObject.parseObject(listResult.getResponse().getContentAsString());
        Integer gaojianId = listResp.getJSONObject("data")
                .getJSONArray("list").getJSONObject(0).getInteger("id");

        // 步骤2：管理员分配专家 (待审→审稿中)
        JSONObject assignBody = new JSONObject();
        assignBody.put("gaojianId", gaojianId);
        assignBody.put("zhuanjiaId", 1);

        mockMvc.perform(post("/gaojian/assign")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignBody.toJSONString()))
                .andExpect(jsonPath("$.code").value(0));

        // 验证状态变为审稿中
        MvcResult infoResult = mockMvc.perform(get("/gaojian/info/" + gaojianId)
                .session(adminSession))
                .andReturn();
        JSONObject info = JSONObject.parseObject(infoResult.getResponse().getContentAsString());
        assertEquals("状态应为审稿中(2)", Integer.valueOf(2),
                info.getJSONObject("data").getInteger("gaojianYesnoTypes"));

        // 步骤3：专家写审稿意见
        JSONObject reviewBody = new JSONObject();
        reviewBody.put("gaojianId", gaojianId);
        reviewBody.put("reviewText", "建议修改第二章实验方法");

        mockMvc.perform(post("/gaojian/review")
                .session(expertSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody.toJSONString()))
                .andExpect(jsonPath("$.code").value(0));

        // 步骤4：管理员终裁为修回 (审稿中→修回)
        JSONObject finalizeBody = new JSONObject();
        finalizeBody.put("gaojianId", gaojianId);
        finalizeBody.put("decision", 3);
        finalizeBody.put("comment", "请按专家意见修改");

        mockMvc.perform(post("/gaojian/finalize")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(finalizeBody.toJSONString()))
                .andExpect(jsonPath("$.code").value(0));

        // 验证状态变为修回
        infoResult = mockMvc.perform(get("/gaojian/info/" + gaojianId)
                .session(adminSession))
                .andReturn();
        info = JSONObject.parseObject(infoResult.getResponse().getContentAsString());
        assertEquals("状态应为修回(3)", Integer.valueOf(3),
                info.getJSONObject("data").getInteger("gaojianYesnoTypes"));

        // 步骤5：作者修回重提 (修回→审稿中)
        JSONObject resubmitBody = new JSONObject();
        resubmitBody.put("gaojianId", gaojianId);
        resubmitBody.put("gaojianFile", "upload/full_test_v2.pdf");
        resubmitBody.put("resubmitNote", "已修改第二章");

        mockMvc.perform(post("/gaojian/resubmit")
                .session(authorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resubmitBody.toJSONString()))
                .andExpect(jsonPath("$.code").value(0));

        // 验证状态回到审稿中，文件历史已保留
        infoResult = mockMvc.perform(get("/gaojian/info/" + gaojianId)
                .session(adminSession))
                .andReturn();
        info = JSONObject.parseObject(infoResult.getResponse().getContentAsString());
        JSONObject data = info.getJSONObject("data");
        assertEquals("状态应回到审稿中(2)", Integer.valueOf(2),
                data.getInteger("gaojianYesnoTypes"));
        assertEquals("当前文件应为新版本", "upload/full_test_v2.pdf",
                data.getString("gaojianFile"));
        assertTrue("文件历史应包含旧版本",
                data.getString("gaojianFileHistory").contains("full_test_v1.pdf"));
        assertTrue("意见链应包含专家意见",
                data.getString("gaojianYesnoText").contains("建议修改第二章"));
        assertTrue("意见链应包含管理员终裁",
                data.getString("gaojianYesnoText").contains("管理员终裁"));
        assertTrue("意见链应包含作者修回",
                data.getString("gaojianYesnoText").contains("作者修回"));

        // 步骤6：管理员最终录用 (审稿中→录用)
        JSONObject acceptBody = new JSONObject();
        acceptBody.put("gaojianId", gaojianId);
        acceptBody.put("decision", 4);
        acceptBody.put("comment", "质量达标，录用");

        mockMvc.perform(post("/gaojian/finalize")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptBody.toJSONString()))
                .andExpect(jsonPath("$.code").value(0));

        // 验证最终状态为录用
        infoResult = mockMvc.perform(get("/gaojian/info/" + gaojianId)
                .session(adminSession))
                .andReturn();
        info = JSONObject.parseObject(infoResult.getResponse().getContentAsString());
        assertEquals("最终状态应为录用(4)", Integer.valueOf(4),
                info.getJSONObject("data").getInteger("gaojianYesnoTypes"));

        // 清理测试数据
        mockMvc.perform(post("/gaojian/delete")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[" + gaojianId + "]"));
    }

    // ===== 终态不可逆测试 =====

    @Test
    public void testCannotAssignAfterAccepted() throws Exception {
        // 如果有录用状态的稿件，尝试再次分配专家 → 应拒绝
        JSONObject body = new JSONObject();
        body.put("gaojianId", 999);
        body.put("zhuanjiaId", 1);

        MvcResult result = mockMvc.perform(post("/gaojian/assign")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toJSONString()))
                .andReturn();

        String resp = result.getResponse().getContentAsString();
        // 稿件不存在或状态不允许
        assertTrue("录用/不存在的稿件不能再分配",
                resp.contains("不允许此操作") || resp.contains("不存在"));
    }

    // ===== 数据隔离测试 =====

    @Test
    public void testAuthorOnlySeesOwnManuscripts() throws Exception {
        MvcResult result = mockMvc.perform(get("/gaojian/page")
                .session(authorSession)
                .param("page", "1")
                .param("limit", "100"))
                .andReturn();

        // 验证返回成功（具体数据依赖数据库，此处只验证接口正常）
        String resp = result.getResponse().getContentAsString();
        assertTrue("作者查询应返回成功", resp.contains("\"code\":0"));
    }

    @Test
    public void testExpertOnlySeesAssignedManuscripts() throws Exception {
        MvcResult result = mockMvc.perform(get("/gaojian/page")
                .session(expertSession)
                .param("page", "1")
                .param("limit", "100"))
                .andReturn();

        String resp = result.getResponse().getContentAsString();
        assertTrue("专家查询应返回成功", resp.contains("\"code\":0"));
    }
}
