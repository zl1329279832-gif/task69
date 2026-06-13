# 盲审流程 Postman 三角色验证说明

> 基础 URL：`http://localhost:8080/gaojiaoguanli`
> 所有请求需携带 Header: `Content-Type: application/json`（登录接口除外）

---

## 前置准备

### 1. 执行数据库变更脚本
```sql
-- 运行 src/main/resources/blind_review.sql
```

### 2. 准备测试数据
- 一个作者账号（zuozhe 表，如 username=`zhangsan`，password=`123456`）
- 一个专家账号（zhuanjia 表，如 username=`lisi`，password=`123456`，zhuanjia_gaojian_types=`1,2,3`）
- 管理员账号（users 表，如 username=`admin`，password=`admin`）

---

## 角色一：作者 — 投稿 + 修回

### 步骤 1：作者登录
```
POST /zuozhe/login
Content-Type: application/x-www-form-urlencoded

username=zhangsan&password=123456
```
**预期响应**：
```json
{"code":0,"token":"xxx","role":"作者","userId":10}
```
> 记下 `token` 和 `userId` 值，后续作者请求均使用此 token。

### 步骤 2：作者投稿
```
POST /gaojian/save
Token: {作者token}
Content-Type: application/json

{
  "gaojianName": "基于深度学习的图像识别研究",
  "gaojianTypes": 1,
  "gaojianContent": "本文探讨了深度学习在图像识别领域的应用",
  "gaojianFile": "/upload/paper_v1.doc"
}
```
**预期**：`code=0`，稿件创建，`gaojianStatusTypes=1`（待审）

### 步骤 3：作者查看自己的稿件
```
GET /gaojian/page?page=1&limit=10
Token: {作者token}
```
**预期**：只返回自己投稿的稿件（数据隔离验证）

### 步骤 4：作者修回重提（在专家审稿结论为"修回"之后）
```
POST /gaojian/revision
Token: {作者token}
Content-Type: application/json

{
  "id": 1,
  "gaojianFile": "/upload/paper_v2.doc",
  "revisionNote": "已根据审稿意见修改了第三章实验部分"
}
```
**预期**：`code=0`，稿件状态回到 `1`（待审），`gaojianFileHistory` 中保留了 v1 版本

### 非法操作验证：作者试图修改已录用稿件
```
POST /gaojian/revision
Token: {作者token}

{"id": 已录用稿件ID, "gaojianFile": "/upload/hack.doc"}
```
**预期**：`code=511`，提示"当前状态不允许修回"

---

## 角色二：管理员 — 分配专家 + 终裁

### 步骤 1：管理员登录
```
POST /users/login
Content-Type: application/x-www-form-urlencoded

username=admin&password=admin
```
**预期**：`code=0`，记下 `token`。

### 步骤 2：查看所有稿件
```
GET /gaojian/page?page=1&limit=10
Token: {管理员token}
```
**预期**：返回所有稿件（管理员看全局）

### 步骤 3：手动分配专家
```
POST /gaojian/assign
Token: {管理员token}
Content-Type: application/json

{"id": 1, "zhuanjiaId": 20}
```
**预期**：`code=0`，稿件状态从 `1`(待审) → `2`(审稿中)

### 步骤 3（替代）：自动匹配专家
```
POST /gaojian/autoAssign
Token: {管理员token}
Content-Type: application/json

{"id": 1}
```
**预期**：`code=0`，系统按 `gaojianTypes` 匹配 + 利益冲突检查 + 负载均衡，自动分配

### 步骤 4：终裁（在专家审稿结论为"修回"之后）
```
POST /gaojian/finalDecision
Token: {管理员token}
Content-Type: application/json

{
  "id": 1,
  "decision": 4,
  "decisionContent": "经终审委员会审议，修改稿符合发表要求，予以录用"
}
```
**预期**：`code=0`，稿件状态变为 `4`（录用）

终裁退稿示例：
```json
{"id": 1, "decision": 5, "decisionContent": "修改后仍不符合学术规范，予以退稿"}
```

### 附加：管理利益冲突
```
POST /liyiChongtu/save
Token: {管理员token}

{"zuozheId": 10, "zhuanjiaId": 20}
```
**预期**：此后自动分配时，专家20不会被分配给作者10的稿件

---

## 角色三：专家 — 审稿

### 步骤 1：专家登录
```
POST /zhuanjia/login
Content-Type: application/x-www-form-urlencoded

username=lisi&password=123456
```
**预期**：`code=0`，记下 `token`。

### 步骤 2：查看分配给自己的稿件
```
GET /gaojian/page?page=1&limit=10
Token: {专家token}
```
**预期**：只返回 `zhuanjia_id=当前专家ID` 的稿件（数据隔离验证）

### 步骤 3：提交审稿意见（结论：修回）
```
POST /gaojian/review
Token: {专家token}
Content-Type: application/json

{
  "id": 1,
  "gaojianShenheContent": "论文整体结构合理，但第三章实验数据不足，建议增加对比实验并补充统计显著性分析。参考文献[5]已过期，建议替换为最新研究。",
  "reviewConclusion": 3
}
```
**预期**：`code=0`，稿件状态变为 `3`（修回），`gaojianYesnoText` 追加一条专家意见记录

### 步骤 3（替代）：提交审稿意见（结论：录用）
```json
{
  "id": 1,
  "gaojianShenheContent": "论文创新性强，实验设计严谨，建议直接录用。",
  "reviewConclusion": 4
}
```
**预期**：稿件状态变为 `4`（录用）

### 步骤 3（替代）：提交审稿意见（结论：退稿）
```json
{
  "id": 1,
  "gaojianShenheContent": "存在严重的方法论问题，核心算法推导有误，建议退稿。",
  "reviewConclusion": 5
}
```
**预期**：稿件状态变为 `5`（退稿）

### 非法操作验证：非分配专家试图审稿
```
POST /gaojian/review
Token: {另一个专家的token}

{"id": 1, "reviewConclusion": 4}
```
**预期**：`code=511`，提示"您不是该稿件的指定审稿专家"

### 非法操作验证：专家通过通用 update 改审稿结果
```
POST /gaojian/update
Token: {专家token}

{"id": 1, "gaojianYesnoTypes": 4, "gaojianStatusTypes": 4}
```
**预期**：`code=511`，提示"专家请使用审稿接口 /gaojian/review"

---

## 完整流程走一遍（Quick Checklist）

| # | 角色 | 操作 | 端点 | 预期状态变化 |
|---|---|---|---|---|
| 1 | 作者 | 投稿 | `POST /gaojian/save` | → 待审(1) |
| 2 | 管理员 | 分配专家 | `POST /gaojian/assign` | 待审(1) → 审稿中(2) |
| 3 | 专家 | 审稿(修回) | `POST /gaojian/review` | 审稿中(2) → 修回(3) |
| 4 | 作者 | 修回重提 | `POST /gaojian/revision` | 修回(3) → 待审(1) |
| 5 | 管理员 | 再次分配 | `POST /gaojian/assign` | 待审(1) → 审稿中(2) |
| 6 | 专家 | 审稿(录用) | `POST /gaojian/review` | 审稿中(2) → 录用(4) |
| 7 | 作者 | 试图修回 | `POST /gaojian/revision` | ❌ 拒绝(511) |

### 终裁路径（替代步骤 5-7）

| # | 角色 | 操作 | 端点 | 预期状态变化 |
|---|---|---|---|---|
| 5' | 管理员 | 终裁(录用) | `POST /gaojian/finalDecision` | 修回(3) → 录用(4) |
| 5'' | 管理员 | 终裁(退稿) | `POST /gaojian/finalDecision` | 修回(3) → 退稿(5) |

---

## 状态机一览

```
         ┌──────────────────────┐
         │                      │
         ▼                      │
      待审(1) ──管理员分配──→ 审稿中(2)
         ▲                      │
         │                      │
      作者修回           专家审稿意见
         │                 /    |    \
         │               /      |      \
    ┌────┘          修回(3)  录用(4)  退稿(5)
    │                  │       ▲       ▲
    │           管理员终裁─→录用(4)或退稿(5)
    │
    └── 修回(3) → 作者修回重提 → 待审(1) [新一轮]
```
