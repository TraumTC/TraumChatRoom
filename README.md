# TraumChatRoom 在线聊天室

> 一个前后端分离的在线聊天室系统，产品代号 **TraumSpace**。内置群聊、私聊、好友系统、AI 助手「小汤」、敏感词过滤、消息撤回、已读未读、@提及提醒、游客模式与管理后台，覆盖实时通信产品的常见能力。

## 目录

- [项目简介](#项目简介)
- [功能清单](#功能清单)
- [技术栈概览](#技术栈概览)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [配置说明与环境变量](#配置说明与环境变量)
- [限流与配额](#限流与配额)
- [测试](#测试)
- [常用命令](#常用命令)

---

## 项目简介

TraumChatRoom 是基于 **Spring Boot + Vue 3** 构建的实时在线聊天室，支持：

- **群聊**：多人实时广播，支持引用回复、@提及提醒、消息撤回（限时）。
- **私聊**：好友间一对一实时通信，支持离线消息、会话级已读/未读统计。
- **AI 助手「小汤」**：在群聊中 `@小汤` 即可触发 DeepSeek 大模型自动回复，带多轮上下文记忆与独立限流。
- **游客模式**：无需注册即可进入群聊体验（限制私聊与 AI），身份信息仅存 Redis。
- **管理后台**：用户管理（软删除 / 改角色 / 禁用 / 重置密码）、敏感词库维护、操作日志审计。
- **安全体系**：JWT 无状态认证 + Refresh Token 轮换（HttpOnly Cookie 下发）、BCrypt 密码、登录失败锁定、多维度限流、请求幂等、敏感词过滤、路径遍历防护、WebSocket 连接鉴权。

数据库为 MySQL（7 张表），Redis 承担在线状态、缓存、限流、幂等、未读等高频数据。

> 昵称变更时同步历史消息冗余字段的逻辑在**应用层**完成（`UserServiceImpl#updateProfile` 与 `AdminController#updateUser`）。早期版本用数据库触发器 `trg_user_name_update` 实现，因会按昵称错误改写语义为 username 的 `receiver_name` 列而移除，存量库需执行 `migration/V3__drop_user_name_trigger.sql`。

## 功能清单

| 模块 | 功能点 | 说明 |
| ---- | ------ | ---- |
| 认证 | 注册 / 登录 / 登出 / Token 刷新 | BCrypt 加密，双 Token（access 30min 仅存前端内存 + refresh 7d 存 HttpOnly Cookie，Redis 按会话记录、刷新时原子轮换，支持单端/全端下线） |
| 认证 | 游客登录 | 免注册体验，身份仅存 Redis（2 小时过期） |
| 用户 | 修改资料 / 修改密码 / 头像 | 改昵称自动同步历史消息冗余字段；头像服务端居中裁剪压缩为 256×256 JPEG，解码前先验 header 尺寸防解压炸弹 |
| 群聊 | 实时消息 / 引用回复 / 上限 2000 字 | STOMP 广播 `/topic/messages` |
| 群聊 | @提及提醒 | 在线实时推送 + 离线未读累计（Redis ZSet，score = messageId，TTL 7 天，上限 50 条） |
| 群聊 | 消息撤回 | 本人或管理员，默认 120 秒窗口，撤回后同步清理 @未读 |
| 群聊 | 历史消息 | 游标分页，支持按消息 ID 锚点定位 |
| 私聊 | 实时消息 / 离线消息 | 非好友仅可在对方在线时发送；好友间可离线投递 |
| 私聊 | 已读 / 未读 | Redis 会话级已读游标（90 天 TTL），未读汇总 + 计数 |
| 好友 | 搜索 / 申请 / 处理 / 备注 / 删除 | 申请 30 天有效，双向唯一关系 |
| AI | @小汤 智能回复 | 调 DeepSeek，多轮上下文（MySQL 最近 5 轮），每用户每分钟限流 |
| 内容安全 | 敏感词过滤 | Trie 树 O(n) 匹配，替换(`***`) / 拦截 两级策略，管理端动态维护 |
| 文件 | 图片 / 文件消息 | 上传（幂等+限流+日志），下载带路径遍历防护 |
| 在线 | 在线用户列表 / 上线下线通知 | Redis ZSet + 心跳（20s），5 分钟无心跳视为离线 |
| 管理 | 用户管理 | 软删除、角色变更、状态禁用、密码重置；小汤与管理员受保护 |
| 管理 | 敏感词管理 / 操作日志 | 日志支持多条件分页查询 |
| 审计 | 操作日志 | AOP 注解式记录，密码 / Token 等敏感字段自动脱敏 |
| 防滥用 | 多维度限流 / 幂等 | 已认证接口按用户名、匿名接口按来源 IP 计数（详见[限流与配额](#限流与配额)）；文件上传与好友申请带幂等键 |

## 技术栈概览

### 后端

| 技术 | 版本 | 用途 |
| ---- | ---- | ---- |
| Java | 21 | 开发语言 |
| Spring Boot | 4.0.5 | 应用框架（Spring MVC / Validation / AOP 内嵌） |
| Spring Security | Boot 管理 | 认证授权（无状态 JWT 模式） |
| JWT (jjwt) | 0.12.6 | access / refresh 双 Token 签发与校验 |
| MyBatis | 4.0.1 (spring-boot-starter) | ORM，XML Mapper 手写 SQL |
| MySQL | Connector-J | 持久化（7 张表） |
| Redis | Spring Data Redis | 在线状态 / 缓存 / 限流 / 幂等 / 未读 / 登录锁定 |
| WebSocket + STOMP | Boot 管理 | 实时消息（SockJS 降级） |
| Spring WebFlux (WebClient) | Boot 管理 | 调用 DeepSeek LLM API |
| AspectJ | Boot 管理 | 操作日志 / 限流 / 幂等 切面 |
| Lombok | 1.18.38 | 简化实体 / DTO |
| Logback | Boot 管理 | 日志（`logback-spring.xml`） |

### 前端

| 技术 | 版本 | 用途 |
| ---- | ---- | ---- |
| Vue | 3.5 | 框架（Composition API + `<script setup>`） |
| Vite | 8.2 | 构建工具 |
| Pinia | 4.0 | 状态管理（auth / chat / websocket） |
| Vue Router | 4.6 | 路由 + 导航守卫（权限控制） |
| Naive UI | 2.40 | 组件库 |
| Tailwind CSS | 3.4 | 原子化样式 |
| STOMP.js + SockJS | 7.3 / 1.6 | WebSocket 实时通信 |
| Axios | 1.19 | HTTP 请求（拦截器自动携带 Token + 幂等头 + 401 自动刷新） |
| vue-virtual-scroller | 2.0-beta | 长列表虚拟滚动 |
| @lucide/vue | 1.29 | 图标 |

## 项目结构

```
TraumChatRoom/
├── src/main/java/com/tc/traumchatroom/
│   ├── TraumChatRoomApplication.java   # 启动类
│   ├── controller/                     # REST 控制器 + WebSocket 控制器
│   │   ├── AuthController.java         #   /api/auth 认证
│   │   ├── UserController.java         #   /api/user 个人中心
│   │   ├── MessageController.java      #   /api/messages 历史/撤回/未读
│   │   ├── FriendController.java       #   /api/friend 好友
│   │   ├── FileController.java         #   /api/file 上传下载
│   │   ├── OnlineController.java       #   /api/online 在线用户
│   │   ├── AdminController.java        #   /api/admin 管理后台
│   │   └── WebSocketChatController.java#   STOMP /app 消息端点
│   ├── service/ + service/impl/        # 业务逻辑
│   ├── mapper/                         # MyBatis Mapper 接口
│   ├── entity/                         # 实体（User/Message/Friend/...）
│   ├── dto/                            # request / response / vo
│   ├── handler/                        # 消息策略处理器 + 敏感词 Trie
│   │   ├── MessageHandlerFactory.java  #   工厂（text/image/file）
│   │   └── SensitiveWordTrie.java      #   前缀树
│   ├── aspect/                         # 幂等 / 限流 / 操作日志切面
│   ├── annotation/                     # @Idempotent / @RateLimit / @LogOperation
│   ├── filter/                         # JwtAuthenticationFilter
│   ├── interceptor/                    # WebSocket 握手拦截器
│   ├── config/                         # 各类配置 + 全局异常处理
│   └── util/                           # JwtUtil / IpUtil / RedisRateLimiter / 游客名等
├── src/main/resources/
│   ├── application.yml / -dev.yml / -prod.yml   # 三套环境配置
│   ├── createTables.sql                         # 建表脚本（全新库用这个）
│   ├── migration/                               # 增量迁移（存量库按序执行）
│   │   ├── V1__p2_indexes.sql                   #   性能索引
│   │   ├── V2__user_last_login_ip.sql           #   最近登录 IP 列
│   │   └── V3__drop_user_name_trigger.sql       #   删除改名触发器 + 修数据
│   ├── mapper/*.xml                             # MyBatis SQL
│   └── logback-spring.xml
├── src/test/java/                      # 单元测试（JUnit 5 + Mockito）
├── ChatRoomVue/                       # 前端
│   ├── src/
│   │   ├── api/        # Axios 封装 + 各模块接口
│   │   ├── stores/     # Pinia（auth / chat / websocket）
│   │   ├── composables/# useWebSocket / useChatHistory / useFileUpload
│   │   ├── components/ # layout / chat / friend / user / admin / ui
│   │   ├── views/      # 首页/登录/注册/聊天室/个人中心/管理页
│   │   ├── router/     # 路由 + 导航守卫
│   │   ├── utils/      # token / 请求ID / 压缩 / 格式化
│   │   └── main.js
│   └── package.json
├── docs/                              # 设计 / 部署文档
├── uploads/                           # 文件上传目录（运行时生成）
└── logs/                              # 运行日志
```

## 快速开始

### 环境要求

| 依赖 | 版本要求 | 说明 |
| ---- | ---- | ---- |
| JDK | 21+ | 后端运行 |
| MySQL | 8.x | 默认库名 `NewChatDB` |
| Redis | 6.x+ | 默认端口 6379 |
| Node.js | 18+ | 前端构建 |
| npm | 9+ | 前端依赖管理 |

### 1. 初始化数据库

全新库直接执行建表脚本：

```bash
mysql -u root -p < src/main/resources/createTables.sql
```

脚本会创建 `user / message / friend / friend_request / ai_conversation_context / sensitive_word / operation_log` 七张表及其索引。

**存量库**（早于本版本创建的）需按序补执行增量迁移：

```bash
mysql -u root -p NewChatDB < src/main/resources/migration/V1__p2_indexes.sql
mysql -u root -p NewChatDB < src/main/resources/migration/V2__user_last_login_ip.sql
mysql -u root -p NewChatDB < src/main/resources/migration/V3__drop_user_name_trigger.sql
```

> 项目未引入 Flyway / Liquibase，迁移需人工执行且不做版本记录 —— 升级后请确认三个脚本都已跑过。

### 2. 启动后端

配置 `JWT_SECRET`（必须，至少 32 字节，否则启动校验会拒绝启动）：

```bash
# Windows PowerShell
$env:JWT_SECRET="your-32-char-secret-key-at-least"
$env:DB_PASSWORD="你的数据库密码"

# 可选
$env:AI_API_KEY="DeepSeek API Key"          # 不使用 AI 可留空
$env:FILE_UPLOAD_DIR="D:\Javawork\TraumChatRoom\uploads"   # 文件目录（Windows 需覆盖默认值）

./mvnw.cmd spring-boot:run
```

启动后访问：`http://localhost:8080`，健康检查见日志。

### 3. 启动前端

```bash
cd ChatRoomVue
npm install
npm run dev
```

访问：`http://localhost:5173`

前端通过 `.env.development` 中的 `VITE_API_URL`（默认 `http://localhost:8080`）直连后端，跨域由后端 CORS 白名单（`cors.allowed-origins`）放行；`vite.config.js` 亦内置了 `/api`、`/ws` 的 Vite 代理作为备用。

### 4. 初始化体验账号

- 注册页可直接注册普通用户。
- 管理员需要手动将某用户 `role` 改为 `ROLE_ADMIN`：
  ```sql
  UPDATE `user` SET role='ROLE_ADMIN' WHERE username='你的管理员用户名';
  ```
- 群聊中 `@小汤` 即可体验 AI 回复（需配置 `AI_API_KEY`）。
- 游客模式：登录页「游客进入」。

## 配置说明与环境变量

所有密钥类配置**不允许硬编码**，统一通过环境变量注入（见 `application-prod.yml`）。

| 环境变量 | 对应配置项 | 默认值 | 必填 | 说明 |
| ---- | ---- | ---- | ---- | ---- |
| `DB_URL` | `spring.datasource.url` | `jdbc:mysql://localhost:3306/NewChatDB` | 否 | JDBC 连接串 |
| `DB_USERNAME` | `spring.datasource.username` | `root` | 否 | 数据库用户 |
| `DB_PASSWORD` | `spring.datasource.password` | 空 | 否 | 数据库密码 |
| `REDIS_HOST` | `spring.data.redis.host` | `localhost` | 否 | Redis 地址 |
| `REDIS_PORT` | `spring.data.redis.port` | `6379` | 否 | Redis 端口 |
| `REDIS_DATABASE` | `spring.data.redis.database` | `0`（dev）/ `5`（prod） | 否 | Redis 库 |
| `REDIS_PASSWORD` | `spring.data.redis.password` | 空 | 否 | Redis 密码 |
| `JWT_SECRET` | `jwt.secret` | 无 | **是** | JWT 签名密钥，至少 32 字节，启动校验 |
| `JWT_ACCESS_EXPIRATION` | `jwt.access-expiration` | `1800000`（30min） | 否 | accessToken 有效期（毫秒） |
| `JWT_REFRESH_COOKIE_NAME` | `jwt.refresh-cookie-name` | `refreshToken` | 否 | refreshToken 的 Cookie 名 |
| `JWT_REFRESH_COOKIE_SECURE` | `jwt.refresh-cookie-secure` | `false`（dev）/ `true`（prod） | 否 | **HTTPS 生产必须为 `true`**，否则 Cookie 会在明文连接上发送 |
| `APP_TRUSTED_PROXIES` | `app.trusted-proxies` | 空（dev）/ `127.0.0.1,::1`（prod） | 生产**是** | 可信反向代理 IP/CIDR，逗号分隔。详见下方[可信代理](#可信代理必读) |
| `AI_API_KEY` | `ai.api.key` | 空 | 否 | DeepSeek API Key |
| `AI_MAX_PER_MINUTE` | `ai.rate-limit.max-per-minute` | `10` | 否 | 每用户每分钟 AI 调用上限 |
| `AI_CONTEXT_RETENTION_DAYS` | `ai.context.retention-days` | `30` | 否 | AI 上下文保留天数（定时任务按此清理） |
| `CHAT_SEND_MAX_PER_MINUTE` | `chat.rate-limit.send-max-per-minute` | `30` | 否 | 每主体每分钟消息发送上限 |
| `CORS_ALLOWED_ORIGINS` | `cors.allowed-origins` | `http://localhost:5173,http://localhost:3000` | 否 | 跨域白名单，逗号分隔，禁止 `*`；HTTP 与 WebSocket 共用此白名单，解析为空会启动失败 |
| `FILE_UPLOAD_DIR` | `file.upload-dir` | `/home/traums/javawork/uploads` | 否 | 文件存储目录（Windows 本地务必覆盖） |

### 可信代理（必读）

`app.trusted-proxies` 决定客户端 IP 怎么解析，而 IP 是**登录失败锁定、注册/游客限流、审计日志**三处的计数依据：

- **留空**（dev 默认）＝ 无条件信任 `X-Forwarded-For`。攻击者伪造该头即可绕过全部 IP 维度的限流与锁定，并污染审计日志。仅适合本地直连开发。
- **已配置**（prod 默认 `127.0.0.1,::1`）＝ 严格模式：只有请求的直接来源（Socket 层的 `remoteAddr`，不可伪造）在可信列表内才采信转发头，且从右往左跳过可信项取真实客户端。

⚠️ 这个值必须与**实际**反向代理地址一致。nginx 与应用同机部署时 `127.0.0.1` 正确；若 nginx 跑在容器桥接网络或独立负载均衡上，`remoteAddr` 会是网关地址而非 `127.0.0.1`，此时转发头被忽略、**所有用户的 IP 会塌缩成同一个网关地址** —— 后果是任意 10 次失败登录就会锁住该地址 30 分钟，等于全站无法登录。请把该地址（如 `172.17.0.1` 或 `10.0.0.0/8`）加入列表。

### 其它常用配置（`application.yml`）

| 配置项 | 默认值 | 说明 |
| ---- | ---- | ---- |
| `jwt.access-expiration` | `1800000`（30min） | accessToken 有效期（毫秒）。短时效 + 仅存前端内存，降低泄露后的可用窗口 |
| `jwt.refresh-expiration` | `604800000`（7d） | refreshToken 有效期（毫秒） |
| `chat.recall-window-seconds` | `120` | 消息撤回时间窗（秒） |
| `file.avatar-max-size` | `5MB` | 头像大小上限 |
| `server.servlet.multipart.max-file-size` | `100MB` | 上传文件大小上限 |
| `spring.profiles.active` | `dev` | 环境；生产必须 `--spring.profiles.active=prod` |

## 限流与配额

所有配额由 `RedisRateLimiter` 统一实现（Lua 原子计数 + TTL，**固定窗口**）。固定窗口在边界处有突刺 —— 窗口末尾打满、窗口一过再打满，极短时间内实际可通过约 2 倍配额；对防滥用这个目标是可接受的取舍。

| 保护对象 | 配额 | 计数维度 | 位置 |
| ---- | ---- | ---- | ---- |
| 消息发送（群聊/私聊） | 30 条/分钟 | 注册用户按 username；**游客按来源 IP** | `WebSocketChatController#allowSend` |
| AI 调用 | 10 次/分钟 | 用户（会话 key `group:{username}`，上下文与配额均按用户隔离） | `AiServiceImpl` |
| 文件上传 | 5 次/分钟 | 用户 | `FileController` |
| 头像上传 | 5 次/分钟 | 用户 | `UserController` |
| 注册 | 10 次/分钟 | **来源 IP** | `AuthController#register` |
| 游客登录 | 5 次/2 小时 | **来源 IP** | `AuthController#guest` |
| 登录失败锁定 | 用户 5 次 → 锁 15 分钟；IP 10 次 → 锁 30 分钟 | 用户名 + IP 双维度 | `AuthServiceImpl#incrementFailCount` |

几点设计说明：

- **匿名接口必须按 IP 计数**。注册与游客登录是 `permitAll`，`Authentication` 是 `AnonymousAuthenticationToken`，`getName()` 恒为 `anonymousUser` —— 按用户名计数会退化成全站共用一个配额，任何人刷满就把所有人挡在门外。因此 `@RateLimit` 提供 `by = By.IP`。
- **游客发送按 IP 而非 username**。游客不入库，每次调 `/api/auth/guest` 都拿到全新的 `guest_xxx`，按 username 计数等于"刷号即重置配额"。
- **游客登录窗口取 2 小时**是刻意与游客会话 TTL 对齐的，因此该计数实际等价于"每 IP 最多 5 个同时存活的游客"，无需另外维护活跃游客集合。
- **Redis 不可用时 fail-closed**：`RedisRateLimiter` 会抛 `SERVICE_UNAVAILABLE`，即 Redis 故障期间受限接口一并拒绝，而不是放开限流。
- IP 维度配额的有效性依赖 [可信代理](#可信代理必读) 配置正确。

### 环境切换

```bash
# 生产环境启动（配合环境变量注入所有密钥）
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## 测试

后端使用 JUnit 5 + Mockito，共 129 个单元测试：

```bash
./mvnw test
```

覆盖重点在纯逻辑与易错边界：JWT 签发校验、敏感词 Trie、IP 解析（含可信代理严格模式）、限流主体解析、在线状态引用计数、Refresh Token 轮换、文件下载路径遍历防护、WebSocket CONNECT 鉴权、撤回权限与时间窗、日志脱敏。

> 当前**没有集成测试**（无 `@SpringBootTest`），因此 MyBatis XML 里的 SQL、Security 过滤器链、事务边界与 STOMP 端到端链路均未被自动化覆盖，这部分改动需手工验证。

## 常用命令

```bash
# 后端构建（跳过测试）
./mvnw package -DskipTests

# 后端测试
./mvnw test

# 只跑单个测试类
./mvnw test -Dtest='RateLimitAspectTest'

# 前端构建产物（dist/）
cd ChatRoomVue && npm run build

# 前端本地预览
npm run preview
```

---

## 附：更多文档

`docs/` 目录下：

| 文档 | 内容 |
| ---- | ---- |
| `01-业务逻辑梳理.md` | 业务流程梳理与初始疑点清单 |
| `02-审查发现清单.md` | 代码审查发现定稿（P0 安全 / P1 功能 / P2 性能，含结论与证据） |
| `03-技术详解与数据流转.md` | 技术栈详解、数据库设计、每个功能点的数据流转、安全与性能设计 |
| `04-全面审查报告.md` | 完整审查报告 |
| `05-技术栈详解-面试版.md` | 面向面试的技术要点与自述稿 |
| `06-生产发布手册-增量.md` | 增量发布步骤与回滚方案 |
| `部署指南.md` | 生产环境部署（含 nginx、环境变量、进程管理） |

## 已知限制

作为单机部署的个人项目，以下是明确的取舍而非待修缺陷：

- **单节点**：STOMP 使用内存版 `SimpleBroker`，上传走本机文件系统。横向扩容需换外部消息代理（RabbitMQ / ActiveMQ STOMP relay）+ 对象存储，否则多实例间用户互相收不到消息。
- **文件下载无鉴权**：`/api/file/download/**` 为 `permitAll` 且不校验请求者与文件的关系，私聊文件的 URL 一旦泄露即长期可访问（文件名为时间戳 + UUID 片段，不可枚举）。
- **迁移无版本管理**：未引入 Flyway / Liquibase，增量 SQL 需人工执行。
- **无 Actuator / 指标端点**：健康状态依赖日志与外部探活。
- **慢速登录爆破**：失败计数是固定窗口，按"每 15 分钟 9 次"的节奏可长期不触发用户维度锁定（密码为 BCrypt 且强制 6-20 位含字母数字，实际风险有限）。
