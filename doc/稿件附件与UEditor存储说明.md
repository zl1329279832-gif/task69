# 稿件附件与 UEditor 存储说明

> 文档版本：v1.0 | 日期：2026-06-13 | 作者：学报编辑部技术组

---

## 一、文档目的

说明稿件管理系统中文件上传的存储路径、命名规则、UEditor 集成方式，以及 Tomcat 重新部署（redeploy）时的数据丢失风险与应对建议。

---

## 二、文件上传架构概览

### 2.1 上传链路

所有文件上传（稿件附件、专家照片、作者照片、UEditor 内嵌图片等）均通过统一接口完成：

```
前端 / UEditor
      |
      | POST /file/upload  (multipart/form-data)
      v
FileController.upload()
      |
      | 写入磁盘
      v
${upload.base-dir}/{timestamp}.{ext}
```

**注意**：UEditor 自带的 `jsp/controller.jsp` 上传通道已禁用（`ueditor.config.js` 中 `serverUrl` 被注释），所有上传统一走 `/file/upload`。

### 2.2 上传目录配置

配置位于 `gaojiaoguanli/src/main/resources/config.properties`：

```properties
upload.base-dir=${catalina.base}/webapps/gaojiaoguanli/upload
```

| 项目 | 说明 |
|------|------|
| `${catalina.base}` | Tomcat 实例根目录（运行时由 Tomcat 解析） |
| 完整路径示例 | `/opt/tomcat/webapps/gaojiaoguanli/upload` |
| 文件命名规则 | `{System.currentTimeMillis()}.{原始扩展名}`，如 `1718265600000.docx` |
| 上传大小限制 | 31 MB（`spring-mvc.xml` 中 `CommonsMultipartResolver.maxUploadSize=32505856`） |
| 访问方式 | `GET /file/download?fileName=xxx`（`@IgnoreAuth`，无需登录即可下载） |

### 2.3 文件在数据库中的存储方式

文件路径以**字符串**形式存储在 `gaojian` 表的以下字段中：

| 字段 | 存储内容 | 示例 |
|------|----------|------|
| `gaojian_file` | 当前稿件附件文件名 | `1718265600000.docx` |
| `gaojian_file_history` | 历史版本文件名 JSON 数组 | `[{"version":1,"file":"1718265500000.docx","time":"..."}]` |

其他实体的照片字段（`zuozhe_photo`、`zhuanjia_photo`）同理，仅存储文件名。

---

## 三、UEditor 集成说明

### 3.1 配置方式

`ueditor.config.js` 关键配置：

```javascript
// UEditor 自带上传已禁用
// , serverUrl: URL + "jsp/controller.jsp"

// 所有上传走 /file/upload 接口
```

### 3.2 UEditor 内容中的文件引用

当作者在稿件介绍（`gaojian_content`，富文本 HTML）中通过 UEditor 插入图片或附件时：

1. 前端调用 `/file/upload` 上传文件，获得文件名（如 `1718265600000.png`）
2. UEditor 将图片标签插入 HTML：`<img src="/file/download?fileName=1718265600000.png" />`
3. HTML 内容存入 `gaojian_content` 字段

**风险点**：`gaojian_content` 中的图片引用依赖磁盘文件存在。若文件被删除或路径变更，富文本内容中的图片将显示为破损链接。

---

## 四、Tomcat Redeploy 风险

### 4.1 问题描述

当前上传目录配置为：

```
${catalina.base}/webapps/gaojiaoguanli/upload
```

该路径位于 Tomcat 的 `webapps` 部署目录内。当执行以下操作时，**upload 目录及其中的所有文件将被删除**：

| 操作 | 是否丢失文件 | 说明 |
|------|:------------:|------|
| Tomcat 正常重启 | 否 | 目录保持不变 |
| 删除旧 WAR 后部署新 WAR | **是** | `webapps/gaojiaoguanli/` 整个目录被替换 |
| Tomcat Manager 点 Redeploy | **是** | 等同于删除旧应用 + 部署新应用 |
| 使用 `ant deploy` / Maven `tomcat7:redeploy` | **是** | WAR 替换会清理整个应用目录 |
| 服务器迁移 / 磁盘更换 | **是** | 如未备份 upload 目录 |

### 4.2 影响范围

一旦 upload 目录丢失，以下功能将受损：

- 所有已上传的稿件附件（`.docx`、`.pdf` 等）无法下载
- 历史版本文件（`gaojian_file_history` 中记录的路径）全部失效
- 作者/专家照片无法显示
- UEditor 富文本内容中的嵌入图片全部破损
- 利益冲突管理等模块中的附件（如有）丢失

### 4.3 根本原因

上传目录使用了**应用部署目录内的相对路径**，而非独立的外部存储。这意味着文件生命周期与应用部署周期耦合。

---

## 五、应对建议

### 5.1 短期措施（运维层面）

1. **部署前备份**：每次 redeploy 前，手动备份 `webapps/gaojiaoguanli/upload/` 目录，部署完成后恢复。
2. **部署脚本自动化**：
   ```bash
   # 部署前
   cp -r $CATALINA_BASE/webapps/gaojiaoguanli/upload /backup/upload_$(date +%Y%m%d)
   # 部署后
   cp -r /backup/upload_latest $CATALINA_BASE/webapps/gaojiaoguanli/upload
   ```

### 5.2 中期措施（配置层面）

将上传目录改为**应用外部的绝对路径**，通过外部配置文件或环境变量覆盖：

```properties
# config.properties（开发环境默认值保留）
upload.base-dir=${catalina.base}/webapps/gaojiaoguanli/upload

# 生产环境通过外部配置覆盖（如 application-prod.properties 或 JVM 参数）
# -Dupload.base-dir=/data/gaojian/upload
```

对应 `FileController` 中的 `@Value("${upload.base-dir}")` 已支持外部化配置，只需修改配置来源即可。

### 5.3 长期措施（架构层面）

| 方案 | 优点 | 缺点 |
|------|------|------|
| 对象存储（MinIO / 阿里云 OSS） | 彻底解耦、高可用、支持 CDN | 需改造上传/下载逻辑 |
| 独立文件服务 | 统一管理、可水平扩展 | 引入新服务依赖 |
| 数据库 BLOB 存储 | 与数据备份统一 | 大文件性能差、数据库膨胀 |

---

## 六、下载接口安全说明

`/file/download` 接口标注了 `@IgnoreAuth`，即**无需登录即可下载任何文件**：

```
GET /file/download?fileName=1718265600000.docx
```

这意味着：
- 任何人只要知道文件名（或猜到时间戳规律），即可下载稿件原文
- 违反盲审保密要求——未授权的专家或外部人员可能获取稿件内容
- 建议后续增加鉴权校验，或至少对下载请求做来源验证

---

## 七、文件清理策略

当前系统**没有文件清理机制**。以下场景会产生孤儿文件：

| 场景 | 说明 |
|------|------|
| 稿件被管理员删除 | `gaojian` 记录删除，但 `upload/` 中对应文件不回收 |
| 作者修回重提 | 旧文件路径存入 `gaojian_file_history`，文件保留在磁盘 |
| UEditor 上传图片后未保存稿件 | 图片已上传但未被任何记录引用 |

长期运行后，upload 目录将持续膨胀。建议定期扫描磁盘文件与数据库记录的对应关系，清理无引用的孤儿文件。
