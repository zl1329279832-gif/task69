# 稿件管理系统 (gaojiaoguanli) 部署指南

> Tomcat 集群迁移版本 — 文件存储外置 + 配置外置 + Log4j 2.x

---

## 目录

1. [前置条件](#1-前置条件)
2. [构建](#2-构建)
3. [数据库准备](#3-数据库准备)
4. [配置外置化](#4-配置外置化)
5. [文件上传目录](#5-文件上传目录)
6. [Tomcat 部署](#6-tomcat-部署)
7. [Nginx 反向代理](#7-nginx-反向代理)
8. [Systemd 服务](#8-systemd-服务)
9. [集群部署注意事项](#9-集群部署注意事项)
10. [验证清单](#10-验证清单)
11. [Log4j 迁移说明](#11-log4j-迁移说明)
12. [回滚方案](#12-回滚方案)
13. [常见问题](#13-常见问题)

---

## 1. 前置条件

| 组件 | 最低版本 | 推荐版本 | 说明 |
|------|---------|---------|------|
| JDK | 1.8 | 1.8.x LTS | **必须 JDK 8+**，Spring 5.0 不兼容 JDK 7 |
| Tomcat | 9.0.x | 9.0.65+ | Servlet 3.0+，支持 DirResourceSet |
| MySQL | 5.6+ | 5.7.x / 8.0 | 集群需共享同一数据库实例 |
| Maven | 3.3+ | 3.8.x | 构建工具 |
| NFS | — | — | 集群环境下共享上传目录 |

### JDK 版本验证

```bash
java -version
# 输出应包含 "1.8" 或 "openjdk version 1.8"

mvn -version
# 输出应包含 "Java version: 1.8" 和 "Maven 3.x"
```

> **Enforcer 插件**：`mvn package` 时会自动检查 JDK 8+ 和 Maven 3.3+，不满足条件构建会失败并给出明确提示。

---

## 2. 构建

```bash
cd gaojiaoguanli

# 完整构建（含 Enforcer 检查）
mvn clean package

# 跳过测试（快速构建）
mvn clean package -DskipTests
```

**产出物**：`target/gaojiaoguanli.war`

### 验证 Log4j 依赖清理

构建完成后，验证 Log4j 1.x 已完全移除：

```bash
# 以下两条命令输出必须为空（无匹配项）
mvn dependency:tree -Dincludes=log4j:log4j
mvn dependency:tree -Dincludes=org.slf4j:slf4j-log4j12

# 确认 Log4j 2.x 已引入
mvn dependency:tree -Dincludes=org.apache.logging.log4j
# 应显示：log4j-api, log4j-core, log4j-slf4j-impl, log4j-1.2-api (均 2.17.1)
```

---

## 3. 数据库准备

### 3.1 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS gaojiaoguanli
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;
```

### 3.2 执行基础建表脚本

```bash
mysql -u root -p gaojiaoguanli < src/main/resources/doc/sys_user.sql
```

### 3.3 执行盲审迁移脚本（幂等）

```bash
mysql -u root -p gaojiaoguanli < src/main/resources/blind_review.sql
```

**幂等性保证**：此脚本可安全重复执行：
- `ALTER TABLE ADD COLUMN` — 先通过 `information_schema` 检查列是否存在，不存在才执行
- `CREATE TABLE` — 使用 `IF NOT EXISTS`
- `INSERT INTO dictionary` — 使用 `WHERE NOT EXISTS` 子查询去重

### 3.4 验证迁移结果

```sql
-- 检查新增字段
SHOW COLUMNS FROM gaojian LIKE 'gaojian_status_types';
SHOW COLUMNS FROM gaojian LIKE 'gaojian_file_history';
SHOW COLUMNS FROM gaojian LIKE 'gaojian_yesno_text';
SHOW COLUMNS FROM zhuanjia LIKE 'zhuanjia_gaojian_types';

-- 检查新表
SHOW TABLES LIKE 'liyi_chongtu';

-- 检查字典数据（应有 5 条）
SELECT * FROM dictionary WHERE dic_code = 'gaojian_status_types';

-- 再次执行脚本验证幂等性
-- 应该无报错、dictionary 数据不增加
```

---

## 4. 配置外置化

### 4.1 原理

Spring `<context:property-placeholder>` 支持多位置加载：

```
加载顺序（后者覆盖前者）：
1. classpath:config.properties          ← WAR 内默认值
2. file:${config.dir}/config.properties ← 外置覆盖（-Dconfig.dir 指定）
3. JVM -D 参数                          ← 最高优先级
```

### 4.2 准备外置配置文件

```bash
# 创建配置目录
mkdir -p /opt/config/prod
mkdir -p /opt/config/staging

# 复制模板并编辑
cp src/main/resources/config/config-prod.properties /opt/config/prod/config.properties
cp src/main/resources/config/config-staging.properties /opt/config/staging/config.properties
```

**编辑 `/opt/config/prod/config.properties`**：

```properties
# 修改为生产数据库连接
jdbc_url=jdbc:mysql://prod-db-host:3306/gaojiaoguanli?useUnicode=true&characterEncoding=UTF-8&tinyInt1isBit=false
jdbc_username=实际用户名
jdbc_password=实际密码

# 上传文件外置目录
upload.base.path=/data/uploads/gaojiaoguanli
```

### 4.3 环境切换

通过 JVM 参数切换环境，**无需重新打包**：

```bash
# 开发（使用 WAR 内默认配置）
# 不设置 config.dir

# 预发布
CATALINA_OPTS="-Dconfig.dir=/opt/config/staging"

# 生产
CATALINA_OPTS="-Dconfig.dir=/opt/config/prod"
```

---

## 5. 文件上传目录

### 5.1 单机环境

```bash
mkdir -p /data/uploads/gaojiaoguanli
chown -R tomcat:tomcat /data/uploads/gaojiaoguanli
chmod 755 /data/uploads/gaojiaoguanli
```

### 5.2 集群环境（NFS 共享）

**NFS Server**（如 192.168.1.200）：

```bash
# /etc/exports
/data/uploads/gaojiaoguanli  192.168.1.0/24(rw,sync,no_subtree_check,no_root_squash)
```

```bash
exportfs -a
systemctl restart nfs-server
```

**各 Tomcat 节点**：

```bash
mkdir -p /data/uploads/gaojiaoguanli
mount -t nfs 192.168.1.200:/data/uploads/gaojiaoguanli /data/uploads/gaojiaoguanli

# 写入 fstab 持久化
echo "192.168.1.200:/data/uploads/gaojiaoguanli /data/uploads/gaojiaoguanli nfs defaults,soft,timeo=30 0 0" >> /etc/fstab
```

### 5.3 首次部署迁移历史文件

```bash
# 将 WAR 内已有上传文件复制到外置目录（仅首次）
cp -rn gaojiaoguanli/src/main/webapp/upload/* /data/uploads/gaojiaoguanli/
```

### 5.4 Tomcat context.xml（关键）

**必须部署** `deploy/context.xml` 到 Tomcat，将外部目录映射为 `/upload` 路径：

```bash
mkdir -p $CATALINA_HOME/conf/Catalina/localhost/
cp deploy/context.xml $CATALINA_HOME/conf/Catalina/localhost/gaojiaoguanli.xml
```

> **注意**：编辑 `gaojiaoguanli.xml` 中的 `docBase` 和 `base` 路径，使其与实际部署路径一致。
>
> 这个 context.xml 是 UEditor 图片正常显示的关键 — 它让 Tomcat 的默认 Servlet 将外部 NFS 目录作为 `/upload/` 静态资源提供服务。

---

## 6. Tomcat 部署

### 6.1 手动部署

```bash
# 1. 停止 Tomcat
$CATALINA_HOME/bin/shutdown.sh

# 2. 备份旧版本
cp $CATALINA_HOME/webapps/gaojiaoguanli.war $CATALINA_HOME/backup/gaojiaoguanli.war.$(date +%Y%m%d)

# 3. 部署新 WAR
rm -rf $CATALINA_HOME/webapps/gaojiaoguanli
cp target/gaojiaoguanli.war $CATALINA_HOME/webapps/

# 4. 设置 JVM 参数
export CATALINA_OPTS="-Dconfig.dir=/opt/config/prod -Xms512m -Xmx1024m -XX:MetaspaceSize=256m"

# 5. 启动
$CATALINA_HOME/bin/startup.sh

# 6. 查看日志
tail -f $CATALINA_HOME/logs/catalina.out
```

### 6.2 使用部署脚本

```bash
chmod +x deploy/deploy.sh

# 部署到生产环境
./deploy/deploy.sh prod

# 部署到预发布环境
./deploy/deploy.sh staging
```

---

## 7. Nginx 反向代理

### 7.1 部署配置

```bash
# 复制并编辑（修改 server_name、SSL 证书路径、上游节点 IP）
cp deploy/nginx-gaojiao.conf /etc/nginx/conf.d/gaojiao.conf

# 验证语法
nginx -t

# 重载
systemctl reload nginx
```

### 7.2 关键配置说明

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `ip_hash` | — | 会话粘性，保持 HttpSession（含 userId/role）不丢失 |
| `client_max_body_size` | 40m | 略大于 Spring 的 31MB multipart 限制 |
| `proxy_read_timeout` | 120s | 审稿流程可能较慢 |

---

## 8. Systemd 服务

### 8.1 安装服务

```bash
# 复制并编辑（修改 JAVA_HOME、CATALINA_HOME、用户等）
cp deploy/tomcat-gaojiao.service /etc/systemd/system/

# 创建 tomcat 用户（如不存在）
useradd -r -s /sbin/nologin tomcat

# 设置目录权限
chown -R tomcat:tomcat /opt/tomcat
chown -R tomcat:tomcat /data/uploads/gaojiaoguanli

# 重载 systemd
systemctl daemon-reload

# 启动并设置开机自启
systemctl start tomcat-gaojiao
systemctl enable tomcat-gaojiao

# 查看状态
systemctl status tomcat-gaojiao

# 查看日志
journalctl -u tomcat-gaojiao -f
```

### 8.2 环境切换

编辑 `/etc/systemd/system/tomcat-gaojiao.service`，修改 `CATALINA_OPTS`：

```ini
# 预发布
Environment="CATALINA_OPTS=-Dconfig.dir=/opt/config/staging"

# 生产
Environment="CATALINA_OPTS=-Dconfig.dir=/opt/config/prod"
```

```bash
systemctl daemon-reload
systemctl restart tomcat-gaojiao
```

### 8.3 安全加固说明

systemd 服务文件包含以下安全配置：
- `NoNewPrivileges=true` — 禁止进程提权
- `PrivateTmp=true` — 隔离 /tmp 目录
- `ReadWritePaths` — 限定可写目录范围

---

## 9. 集群部署注意事项

### 9.1 架构图

```
                    ┌─────────────┐
                    │   Nginx     │
                    │ (ip_hash)   │
                    └──────┬──────┘
                    ┌──────┴──────┐
              ┌─────┴─────┐ ┌────┴──────┐
              │ Tomcat-01 │ │ Tomcat-02 │
              └─────┬─────┘ └────┬──────┘
                    │            │
         ┌──────────┴────────────┴──────────┐
         │          NFS 共享存储             │
         │   /data/uploads/gaojiaoguanli    │
         └──────────────────────────────────┘
                    │
              ┌─────┴─────┐
              │  MySQL    │
              │ (共享实例) │
              └───────────┘
```

### 9.2 关键约束

| 项目 | 说明 |
|------|------|
| **会话粘性** | Nginx 必须配置 `ip_hash`，`AuthorizationInterceptor` 将认证信息存入 HttpSession |
| **共享上传目录** | 所有节点 NFS 挂载同一目录，`context.xml` 的 `base` 路径一致 |
| **数据库连接池** | Druid `maxActive=20`/节点，确保 MySQL `max_connections >= 节点数 × 20 + 缓冲` |
| **文件命名** | 使用 `new Date().getTime()` 毫秒时间戳，集群冲突概率极低 |
| **静态资源** | 打包在 WAR 内（`/resources/`），各节点独立部署，无冲突 |
| **日志** | 各节点独立写入 `${catalina.home}/logs/yo_log/`，如需集中可配置 ELK/rsyslog |
| **字典缓存** | `DictionaryServletContextListener` 各节点独立缓存，启动时从共享 DB 加载 |

### 9.3 新增节点检查清单

1. [ ] NFS 挂载 `/data/uploads/gaojiaoguanli`
2. [ ] 部署 `gaojiaoguanli.war`
3. [ ] 部署 `gaojiaoguanli.xml` (context.xml)
4. [ ] 配置 `/opt/config/prod/config.properties`
5. [ ] 启动 Tomcat
6. [ ] Nginx upstream 添加新节点 IP

---

## 10. 验证清单

### 10.1 基础功能

- [ ] 访问 `http://host/gaojiaoguanli/jsp/login.jsp` 加载正常
- [ ] 管理员登录成功
- [ ] 作者登录成功
- [ ] 专家登录成功

### 10.2 文件上传（核心验证）

- [ ] 作者上传稿件 PDF → 文件落盘到外置目录 `/data/uploads/gaojiaoguanli/`
- [ ] 作者上传头像图片 → 图片正常显示
- [ ] UEditor 富文本编辑器中插入图片 → 图片通过 `/upload/` 路径正常显示
- [ ] 稿件附件下载 → 正常下载

### 10.3 盲审流程

- [ ] 作者提交稿件 → 状态变为「待审」
- [ ] 系统自动分配专家（匹配学科方向 + 排除利益冲突 + 负载均衡）
- [ ] 专家收到分配 → 状态变为「审稿中」
- [ ] 专家提交审稿意见 → 可退回修改或建议录用
- [ ] 作者修改后重新提交 → 状态变为「修回」
- [ ] 最终录用/退稿决定

### 10.4 集群验证

- [ ] 在节点 A 上传文件 → 节点 B 可以下载
- [ ] 在节点 A 登录 → 请求被 Nginx 路由到同一节点（会话粘性）
- [ ] 停止节点 A → Nginx 自动将请求路由到节点 B

---

## 11. Log4j 迁移说明

### 11.1 变更概述

| 项目 | 变更前 | 变更后 |
|------|--------|--------|
| 日志框架 | Log4j 1.2.17 | Log4j 2.17.1 |
| SLF4J 桥接 | slf4j-log4j12 | log4j-slf4j-impl |
| 兼容桥接 | 无 | log4j-1.2-api（兼容使用 org.apache.log4j 的第三方库） |
| 配置文件 | log4j.properties | log4j2.xml |
| Root 级别 | DEBUG | INFO（减少生产日志量） |
| 日志轮转 | DailyRollingFileAppender | RollingFile + TimeBasedTriggeringPolicy |
| 自动清理 | 无 | 90 天自动删除 |

### 11.2 为什么安全

1. **应用代码零修改**：所有业务代码通过 `SLF4J Logger` 写日志，桥接层从 `slf4j-log4j12` 换为 `log4j-slf4j-impl`，对调用方透明
2. **第三方库兼容**：`log4j-1.2-api` 桥接 JAR 提供 `org.apache.log4j.Logger` 等类的兼容实现，百度 SDK 等使用 Log4j 1.x API 的库不会报 `ClassNotFoundException`
3. **配置等价迁移**：`log4j2.xml` 完整映射了原 `log4j.properties` 的所有 Appender 和 Logger

### 11.3 验证方法

```bash
# 1. 确认 Log4j 1.x 不在依赖树中
mvn dependency:tree -Dincludes=log4j:log4j
# 期望输出：无匹配项

mvn dependency:tree -Dincludes=org.slf4j:slf4j-log4j12
# 期望输出：无匹配项

# 2. 确认 Log4j 2.x 已引入
mvn dependency:tree -Dincludes=org.apache.logging.log4j
# 期望输出：log4j-api, log4j-core, log4j-slf4j-impl, log4j-1.2-api

# 3. 运行时验证：启动 Tomcat 后检查日志
# - 控制台应输出 Log4j2 初始化信息
# - 日志文件应写入 ${catalina.home}/logs/yo_log/PurePro_current.log
# - SQL 语句日志应在 DEBUG 级别输出

# 4. 如果运行时出现 ClassNotFoundException: org.apache.log4j.*
# 说明 log4j-1.2-api 桥接未生效，检查是否被其他依赖排除
mvn dependency:tree | grep "log4j-1.2-api"
```

### 11.4 回退方案

如果 Log4j 2.x 迁移出现问题，可以回退：

1. 恢复 pom.xml 中的 Log4j 1.x 依赖
2. 恢复 `log4j.properties` 文件
3. 删除 `log4j2.xml`
4. 重新 `mvn clean package`

---

## 12. 回滚方案

### 12.1 快速回滚

```bash
# 1. 停止 Tomcat
systemctl stop tomcat-gaojiao

# 2. 恢复上一版本 WAR
cp $CATALINA_HOME/backup/gaojiaoguanli.war.YYYYMMDD $CATALINA_HOME/webapps/gaojiaoguanli.war
rm -rf $CATALINA_HOME/webapps/gaojiaoguanli

# 3. 移除外置 context.xml（旧版本不需要）
rm -f $CATALINA_HOME/conf/Catalina/localhost/gaojiaoguanli.xml

# 4. 启动
systemctl start tomcat-gaojiao
```

### 12.2 数据库回滚

盲审迁移脚本是增量变更，回滚需要手动：

```sql
-- 删除新增字典数据
DELETE FROM dictionary WHERE dic_code = 'gaojian_status_types';

-- 删除新增表
DROP TABLE IF EXISTS liyi_chongtu;

-- 删除新增字段
ALTER TABLE gaojian DROP COLUMN gaojian_status_types;
ALTER TABLE gaojian DROP COLUMN gaojian_file_history;
ALTER TABLE gaojian DROP COLUMN gaojian_yesno_text;
ALTER TABLE zhuanjia DROP COLUMN zhuanjia_gaojian_types;
```

> **警告**：回滚会丢失已产生的审稿数据，仅在确认无业务数据时使用。

---

## 13. 常见问题

### Q: UEditor 图片显示 404

**原因**：未部署 `context.xml` 或 `base` 路径错误。

**解决**：
1. 确认 `$CATALINA_HOME/conf/Catalina/localhost/gaojiaoguanli.xml` 存在
2. 确认 `base` 路径指向实际上传目录
3. 确认上传目录中有文件
4. 重启 Tomcat 使 context.xml 生效

### Q: 上传文件报 "上传文件不能为空"

**原因**：Nginx 的 `client_max_body_size` 小于 Spring 的 multipart 限制。

**解决**：确认 Nginx 配置 `client_max_body_size 40m;`

### Q: 集群环境下切换节点后需要重新登录

**原因**：Nginx 未配置 `ip_hash` 或客户端 IP 经过代理变化。

**解决**：
1. 确认 Nginx upstream 使用 `ip_hash`
2. 如果经过 CDN/代理，考虑使用 Tomcat 的 `Manager` 组件做 session 复制

### Q: mvn package 报错 "requireJavaVersion"

**原因**：JDK 版本低于 8。

**解决**：安装 JDK 8+ 并设置 `JAVA_HOME`

### Q: 外置配置不生效

**原因**：`config.dir` 系统属性未正确传递。

**验证**：
```bash
# 检查 Tomcat 启动参数
ps aux | grep tomcat | grep config.dir

# 或在应用中添加调试日志，检查实际加载的 jdbc_url
```
