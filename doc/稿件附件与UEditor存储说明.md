# 稿件附件与 UEditor 存储说明

> 基于 `FileController.java`、`CommonController.java`、`config.properties`、`ueditor.config.js` 源码编写。

---

## 一、文件上传架构

### 1.1 上传接口

| 接口 | 路径 | 说明 |
|------|------|------|
| 稿件/图片上传 | `POST /file/upload` | `FileController.java`，接收 `MultipartFile`，返回文件名 |
| 文件下载 | `GET /file/download?fileName=xxx` | `FileController.java`，按文件名读取并响应二进制流 |

UEditor 富文本编辑器的 `serverUrl`（原 `jsp/controller.jsp`）已被注释掉，前端图片/附件上传统一走 `/file/upload` 接口。

### 1.2 文件命名规则

```java
// FileController.java 第 57-58 行
String fileExt = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".")+1);
String fileName = new Date().getTime() + "." + fileExt;
```

- 文件以 **上传时刻的毫秒时间戳** 作为文件名，保留原始扩展名
- 示例：`1718234567890.docx`、`1718234568123.pdf`
- **风险**：并发上传时同一毫秒可能产生文件名冲突导致覆盖

### 1.3 数据库存储

稿件实体中与文件相关的字段：

| 字段 | 存储内容 | 示例 |
|------|----------|------|
| `gaojianFile` | 当前版本文件名（不含路径） | `1718234567890.docx` |
| `gaojianFileHistory` | 历史版本 JSON 数组 | `[{"version":1,"file":"1718200000000.docx","time":"2024-06-12 10:00:00"}]` |
| `gaojianContent` | 稿件介绍（UEditor 富文本 HTML） | `<p>本文研究...</p><img src="/file/download?fileName=xxx.png">` |

---

## 二、上传路径配置

### 2.1 配置位置

```properties
# config.properties
upload.base-dir=${catalina.base}/webapps/gaojiaoguanli/upload
```

通过 Spring `@Value("${upload.base-dir}")` 注入到 `FileController` 和 `CommonController`。

### 2.2 实际磁盘路径

以典型 Tomcat 安装为例：

```
${catalina.base}
└── webapps/
    └── gaojiaoguanli/          ← WAR 解压目录
        ├── WEB-INF/
        ├── resources/
        │   └── ueditor/        ← UEditor 静态资源
        └── upload/             ← ★ 文件上传目录 ★
            ├── 1718234567890.docx
            ├── 1718234568123.pdf
            └── ...
```

### 2.3 UEditor 配置说明

```javascript
// ueditor.config.js 第 33-36 行
// 注意：本项目文件上传走 /file/upload 接口（FileController），
// 不使用 UEditor 自带的 jsp/controller.jsp。上传文件存储在外置目录，
// 由 config.properties 中 upload.base-dir 配置。
// , serverUrl: URL + "jsp/controller.jsp"
```

UEditor 内置的后端上传功能已禁用，所有文件操作通过项目自定义的 `FileController` 完成。

---

## 三、Tomcat Redeploy 风险

### 3.1 问题描述

上传目录 `upload/` 位于 WAR 解压后的应用目录内部（`webapps/gaojiaoguanli/upload/`）。当发生以下操作时，**已上传的全部稿件文件将被永久删除**：

| 操作 | 后果 |
|------|------|
| `mvn clean package` + 重新部署 WAR | Tomcat undeploy 旧版本时清空整个 `gaojiaoguanli/` 目录 |
| Tomcat Manager 界面点击 "Undeploy" | 同上 |
| 删除 `webapps/gaojiaoguanli.war` 后重启 | Tomcat 自动清理对应解压目录 |
| Tomcat `autoDeploy=true` + 替换 WAR 文件 | 热部署时先删除旧目录再解压新 WAR |
| 服务器迁移未备份 `upload/` | 数据丢失 |

### 3.2 影响范围

- 所有通过 `/file/upload` 上传的稿件原文（`.docx`、`.pdf` 等）
- UEditor 编辑器中插入的图片（`gaojianContent` 中的 `<img>` 引用将变为 404）
- 人脸比对照片（`CommonController.matchFace` 使用同一 `uploadBaseDir`）

### 3.3 建议修复方案

将上传目录配置为 **WAR 包外部路径**，与应用生命周期解耦：

```properties
# 生产环境推荐配置（覆盖 config.properties）
upload.base-dir=/data/gaojiaoguanli/upload
```

同时在 Tomcat 中配置静态资源映射（或通过 Nginx 代理），使 `/file/download` 能访问外部目录。

---

## 四、UEditor 富文本中的资源引用

### 4.1 图片引用方式

UEditor 编辑器插入图片后，HTML 中的引用路径格式为：

```html
<img src="/gaojiaoguanli/file/download?fileName=1718234599999.png">
```

或者前端拼接的相对路径。由于走的是 `/file/download` 接口（`@IgnoreAuth` 无需登录），任何知道文件名的人均可直接下载。

### 4.2 富文本内容存储

- `gaojianContent` 字段存储完整 HTML（含 `<img>`、`<a>` 等标签）
- HTML 存储在 MySQL TEXT 列中，无大小限制检查
- UEditor 的 XSS 过滤依赖前端配置，后端未做 HTML 白名单净化

---

## 五、已知问题汇总

| 编号 | 问题 | 严重程度 | 说明 |
|:----:|------|:--------:|------|
| S-1 | **Redeploy 丢失全部附件** | 严重 | 上传目录在 WAR 包内部，部署即清空 |
| S-2 | **文件名时间戳冲突** | 中等 | 并发上传同毫秒文件互相覆盖 |
| S-3 | **下载接口无鉴权** | 中等 | `/file/download` 标记 `@IgnoreAuth`，任何人可通过文件名直接下载稿件 |
| S-4 | **无文件类型白名单** | 中等 | 上传接口不校验文件扩展名和 MIME 类型，可上传 `.jsp`、`.sh` 等危险文件 |
| S-5 | **UEditor XSS 风险** | 低 | 富文本 HTML 直接存入数据库并在前端渲染，后端未做白名单过滤 |
| S-6 | **无文件大小限制** | 低 | 未配置 Spring `multipart.max-file-size`，大文件可耗尽磁盘/内存 |
