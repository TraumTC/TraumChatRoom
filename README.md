# TraumChatRoom 在线聊天室

> 一个前后端分离的在线聊天室系统，产品代号 **TraumSpace**。内置群聊、私聊、好友系统、AI 助手「小汤」、敏感词过滤、消息撤回、已读未读、@提及提醒、游客模式与管理后台，覆盖实时通信产品的常见能力。

## 目录

- [项目简介](#项目简介)
- [功能清单](#功能清单)
- [技术栈概览](#技术栈概览)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [配置说明与环境变量](#配置说明与环境变量)
- [常用命令](#常用命令)

---

## 项目简介

TraumChatRoom 是基于 **Spring Boot + Vue 3** 构建的实时在线聊天室，支持：

- **群聊**：多人实时广播，支持引用回复、@提及提醒、消息撤回（限时）。
- **私聊**：好友间一对一实时通信，支持离线消息、会话级已读/未读统计。
- **AI 助手「小汤」**：在群聊中 `@小汤` 即可触发 DeepSeek 大模型自动回复，带多轮上下文记忆与独立限流。
- **游客模式**：无需注册即可进入群聊体验（限制私聊与 AI），身份信息仅存 Redis。
- **管理后台**：用户管理（软删除 / 改角色 / 禁用 / 重置密码）、敏感词库维护、操作日志审计。
- **安全体系**：JWT 无状态认证 + Refresh Token、BCrypt 密码、登录失败锁定、接口限流、请求幂等、敏感词过滤、路径遍历防护。

数据库为 MySQL（7 张表 + 1 触发器），Redis 承担在线状态、缓存、限流、幂等、未读等高频数据。

## 功能清单

| 模块 | 功能点 | 说明 |
| ---- | ------ | ---- |
| 认证 | 注册 / 登录 / 登出 / Token 刷新 | BCrypt 加密，双 Token（access 24h + refresh 7d，refresh 存 Redis 可主动失效） |
| 认证 | 游客登录 | 免注册体验，身份仅存 Redis（2 小时过期） |
| 用户 | 修改资料 / 修改密码 / 头像 | 改昵称自动同步历史消息冗余字段；头像服务端压缩为 256×256 |
| 群聊 | 实时消息 / 引用回复 / 上限 2000 字 | STOMP 广播 `/topic/messages` |
| 群聊 | @提及提醒 | 在线实时推送 + 离线未读累计（Redis List，TTL 7 天） |
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
│   └── util/                           # JwtUtil / RedisRateLimiter / 游客名等
├── src/main/resources/
│   ├── application.yml / -dev.yml / -prod.yml   # 三套环境配置
│   ├── createTables.sql                         # 建表脚本
│   ├── mapper/*.xml                             # MyBatis SQL
│   └── logback-spring.xml
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

```bash
mysql -u root -p < src/main/resources/createTables.sql
```

脚本会创建 `user / message / friend / friend_request / ai_conversation_context / sensitive_word / operation_log` 七张表，以及改昵称时同步历史消息的触发器 `trg_user_name_update`。

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
| `REDIS_DATABASE` | `spring.data.redis.database` | 空（dev）`5`（prod） | 否 | Redis 库 |
| `REDIS_PASSWORD` | `spring.data.redis.password` | 空 | 否 | Redis 密码 |
| `JWT_SECRET` | `jwt.secret` | 无 | **是** | JWT 签名密钥，至少 32 字节，启动校验 |
| `AI_API_KEY` | `ai.api.key` | 空 | 否 | DeepSeek API Key |
| `AI_MAX_PER_MINUTE` | `ai.rate-limit.max-per-minute` | `10` | 否 | 每用户每分钟 AI 调用上限 |
| `CHAT_SEND_MAX_PER_MINUTE` | `chat.rate-limit.send-max-per-minute` | `30` | 否 | 每用户每分钟消息发送上限 |
| `CORS_ALLOWED_ORIGINS` | `cors.allowed-origins` | `http://localhost:5173,http://localhost:3000` | 否 | 跨域白名单，逗号分隔，禁止 `*` |
| `FILE_UPLOAD_DIR` | `file.upload-dir` | `/home/traums/javawork/uploads` | 否 | 文件存储目录（Windows 本地务必覆盖） |

### 其它常用配置（`application.yml`）

| 配置项 | 默认值 | 说明 |
| ---- | ---- | ---- |
| `jwt.access-expiration` | `86400000`（24h） | accessToken 有效期（毫秒） |
| `jwt.refresh-expiration` | `604800000`（7d） | refreshToken 有效期（毫秒） |
| `chat.recall-window-seconds` | `120` | 消息撤回时间窗（秒） |
| `file.avatar-max-size` | `5MB` | 头像大小上限 |
| `server.servlet.multipart.max-file-size` | `100MB` | 上传文件大小上限 |
| `spring.profiles.active` | `dev` | 环境；生产必须 `--spring.profiles.active=prod` |

### 环境切换

```bash
# 生产环境启动（配合环境变量注入所有密钥）
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## 常用命令

```bash
# 后端构建（跳过测试）
./mvnw package -DskipTests

# 后端测试
./mvnw test

# 前端构建产物（dist/）
cd ChatRoomVue && npm run build

# 前端本地预览
npm run preview
```

---

## 附：更多文档

- `docs/03-技术详解与数据流转.md`：技术栈详解、数据库设计、每个功能点的数据流转、安全与性能设计、面试亮点。
- `docs/部署指南.md`：生产环境部署。
