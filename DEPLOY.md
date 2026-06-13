# 学报管理系统 (gaojiaoguanli) - 部署指南

## 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 8+ | 编译和运行均需 JDK 8，pom.xml 已配置 enforcer 强制校验 |
| Tomcat | 9.0.x | 推荐 9.0.85+，需配置外部配置文件路径 |
| MySQL | 5.7+ / 8.0 | 字符集 UTF-8，数据库名 `gaojiaoguanli` |
| Maven | 3.6+ | 构建用 |

---

## 1. 构建 WAR

```bash
cd gaojiaoguanli
mvn clean package -DskipTests
# 产出: target/gaojiaoguanli.war
```

CI 流水线示例：

```bash
# 1) 编译打包
mvn clean package -DskipTests

# 2) JDK 版本校验（enforcer 已绑定 validate 阶段，package 时自动执行）
#    如需单独校验：
mvn enforcer:enforce

# 3) 确认 WAR 产物
ls -lh target/gaojiaoguanli.war

# 4) Flyway 迁移校验（需要数据库连接，CI 中可用测试库）
mvn flyway:info \
  -Dflyway.url=jdbc:mysql://ci-db:3306/gaojiaoguanli \
  -Dflyway.user=ci_user \
  -Dflyway.password=ci_pass

# 5) 安全依赖扫描（可选）
mvn org.owasp:dependency-check-maven:check
```

---

## 2. 外部配置文件

WAR 内 `config.properties` 包含开发环境默认值。生产环境通过外部文件覆盖：

```bash
# 复制配置模板
cp conf/gaojiaoguanli-example.properties ${CATALINA_BASE}/conf/gaojiaoguanli.properties

# 编辑生产配置
vim ${CATALINA_BASE}/conf/gaojiaoguanli.properties
```

**关键配置项**：

```properties
# 数据库连接（必改）
jdbc_url=jdbc:mysql://db-prod:3306/gaojiaoguanli?useUnicode=true&characterEncoding=UTF-8&useSSL=true
jdbc_username=gaojiaoguanli_app
jdbc_password=<生产密码>

# 文件上传目录（必改，绝对路径）
upload.base-dir=/data/gaojiaoguanli/upload
```

**加载优先级**：外部 `${CATALINA_BASE}/conf/gaojiaoguanli.properties` 的值覆盖 WAR 内默认值。如果外部文件不存在，自动回退到 WAR 内配置（方便开发环境）。

---

## 3. 文件上传目录

上传文件（稿件 PDF、UEditor 附件）现在存储在 WAR 外部目录，重新部署 WAR 不会丢失文件。

```bash
# 创建上传目录
mkdir -p /data/gaojiaoguanli/upload

# 设置权限（tomcat 用户需要读写权限）
chown -R tomcat:tomcat /data/gaojiaoguanli/upload
chmod 750 /data/gaojiaoguanli/upload
```

**从旧版迁移**：如果之前的稿件文件在 WAR 目录下，需要手动迁移：

```bash
# 迁移旧文件（WAR 展开目录 → 外部目录）
cp -rp ${CATALINA_BASE}/webapps/gaojiaoguanli/upload/* /data/gaojiaoguanli/upload/
chown -R tomcat:tomcat /data/gaojiaoguanli/upload/
```

---

## 4. 数据库迁移 (Flyway)

系统使用 Flyway 管理数据库 schema 变更。迁移脚本位于 `src/main/resources/db/migration/`。

### 首次部署（已有数据库）

Flyway 配置了 `baselineOnMigrate=true`，首次启动时自动创建 `flyway_schema_history` 表并以 V1 为基线。V2 (blind_review) 脚本会自动执行。

### 迁移脚本说明

| 版本 | 文件 | 说明 |
|------|------|------|
| V1 | `V1__baseline.sql` | 空基线标记，代表已有 schema |
| V2 | `V2__blind_review.sql` | 盲审流程字段（幂等：可重复执行） |

### 手动执行迁移（可选）

```bash
# 查看迁移状态
mvn flyway:info -Dflyway.url=... -Dflyway.user=... -Dflyway.password=...

# 手动执行迁移
mvn flyway:migrate -Dflyway.url=... -Dflyway.user=... -Dflyway.password=...

# 校验脚本一致性
mvn flyway:validate -Dflyway.url=... -Dflyway.user=... -Dflyway.password=...
```

应用启动时 Flyway 也会自动执行 pending 的迁移（在 MyBatis 初始化之前）。

---

## 5. Log4j2 迁移验证

本次将 Log4j 1.2.17（EOL）迁移到 Log4j2 2.23.1，以通过安全扫描。

### 迁移内容

- **依赖替换**：`log4j:log4j` → `log4j-core` + `log4j-api` + `log4j-slf4j-impl`
- **配置文件**：`log4j.properties` → `log4j2.xml`
- **业务代码零改动**：所有业务代码通过 SLF4J 调用（`LoggerFactory.getLogger()`），不受影响

### 验证步骤

```bash
# 1) 确认依赖树中只有 log4j2
mvn dependency:tree | grep -i log4j
# 应看到: org.apache.logging.log4j:log4j-api:2.23.1
#         org.apache.logging.log4j:log4j-core:2.23.1
#         org.apache.logging.log4j:log4j-slf4j-impl:2.23.1
# 不应看到: log4j:log4j:1.2.17 或 slf4j-log4j12

# 2) 启动 Tomcat，检查控制台
# 不应出现: "log4j:WARN No appenders could be found"
# 应出现正常日志格式: [gaojiaoguanli] 2024-xx-xx ...

# 3) 检查日志文件
ls ${CATALINA_BASE}/logs/gaojiaoguanli/
# 应看到: gaojiaoguanli.log

# 4) OWASP Dependency-Check（可选）
mvn org.owasp:dependency-check-maven:check
# Log4j 相关 CVE 不应再出现
```

### Log4j 1.x CVE 说明

| CVE | 受影响组件 | 本项目是否使用 | 实际风险 |
|-----|-----------|-------------|---------|
| CVE-2019-17571 | SocketServer | 否 | 无 |
| CVE-2022-23302 | JMSSink | 否 | 无 |
| CVE-2022-23305 | JDBCAppender | 否 | 无 |
| CVE-2022-23307 | Chainsaw | 否 | 无 |

虽然实际攻击面为零，但安全扫描工具按 GAV 坐标匹配，无法区分。迁移到 Log4j2 是通过扫描的唯一办法。

---

## 6. 部署步骤（完整流程）

```bash
# 1. 构建
cd gaojiaoguanli && mvn clean package -DskipTests

# 2. 准备外部目录
mkdir -p /data/gaojiaoguanli/upload
chown tomcat:tomcat /data/gaojiaoguanli/upload

# 3. 部署外部配置
cp conf/gaojiaoguanli-example.properties ${CATALINA_BASE}/conf/gaojiaoguanli.properties
vim ${CATALINA_BASE}/conf/gaojiaoguanli.properties   # 编辑生产配置

# 4. 迁移旧上传文件（仅首次迁移时）
cp -rp ${CATALINA_BASE}/webapps/gaojiaoguanli/upload/* /data/gaojiaoguanli/upload/

# 5. 部署 WAR
cp target/gaojiaoguanli.war ${CATALINA_BASE}/webapps/

# 6. 启动/重启 Tomcat
systemctl restart gaojiaoguanli-tomcat   # 或 ${CATALINA_BASE}/bin/shutdown.sh && startup.sh

# 7. 验证
curl -s http://localhost:8080/gaojiaoguanli/ | head -5
tail -f ${CATALINA_BASE}/logs/gaojiaoguanli/gaojiaoguanli.log
```

---

## 7. systemd 服务管理

参考 `deploy/gaojiaoguanli-tomcat.service`，将其复制到 `/etc/systemd/system/`：

```bash
cp deploy/gaojiaoguanli-tomcat.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable gaojiaoguanli-tomcat
systemctl start gaojiaoguanli-tomcat
```

---

## 目录结构说明

```
生产服务器目录布局：

/opt/tomcat/                          ← CATALINA_BASE
  ├── conf/
  │   ├── server.xml
  │   └── gaojiaoguanli.properties    ← 外部配置（覆盖WAR内默认值）
  ├── webapps/
  │   └── gaojiaoguanli.war           ← WAR 包（可重复覆盖部署）
  └── logs/
      └── gaojiaoguanli/
          └── gaojiaoguanli.log       ← 应用日志（Log4j2）

/data/gaojiaoguanli/
  └── upload/                         ← 稿件文件（独立于WAR，部署不丢失）
      ├── 1234567890.pdf
      └── ...
```
