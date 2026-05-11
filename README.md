# TraumChatRoom 聊天室

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-green.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> 一个基于 Spring Boot + WebSocket 的实时聊天室应用，支持群聊、私聊、文件传输等功能。

🔗 **在线体验**: [https://traums.cn/](https://traums.cn/)

📦 **GitHub 仓库**: [TraumTC/TraumChatRoom](https://github.com/TraumTC/TraumChatRoom)

---

## ✨ 功能特性

### 核心功能
- 💬 **实时群聊** - 基于 WebSocket 的多人实时聊天
- 🔒 **私聊功能** - 支持一对一私密消息
- 👤 **用户认证** - JWT + Spring Security 安全认证
- 🎭 **游客模式** - 无需注册即可匿名体验
- 📎 **文件传输** - 支持图片、文档等文件上传下载 (最大 100MB)
- 📜 **历史消息** - 支持查看近期聊天记录
- 🔔 **@提及功能** - 快速提及在线用户

### 管理功能
- 👑 **管理员后台** - 用户管理、角色分配
- 📝 **个人资料** - 修改昵称、密码
- 👥 **在线用户** - 实时显示在线用户列表

---

## 🛠 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.5 | 核心框架 |
| Spring Security | - | 安全认证与授权 |
| Spring WebSocket | - | 实时通信 (STOMP) |
| JWT (jjwt) | 0.12.6 | 无状态认证 |
| MyBatis | 4.0.1 | ORM 持久层 |
| MySQL | - | 数据存储 |
| Thymeleaf | - | 模板引擎 |
| Lombok | - | 代码简化 |

---

## 🚀 快速开始

### 环境要求
- JDK 21+
- MySQL 8.0+
- Maven 3.6+

### 1. 克隆仓库
```bash
git clone https://github.com/TraumTC/TraumChatRoom.git
cd TraumChatRoom
```

### 2. 配置数据库
创建 MySQL 数据库 `ChatDB`，并修改 `application.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ChatDB
    username: root
    password: your_password
```

### 3. 运行项目
```bash
./mvnw spring-boot:run
```

或打包后运行：
```bash
./mvnw clean package
java -jar target/TraumChatRoom-0.0.1-SNAPSHOT.jar
```

### 4. 访问应用
- 首页: http://localhost:8080/
- 聊天室: http://localhost:8080/space
- 管理后台: http://localhost:8080/admin/users (需要管理员权限)

---

## 📁 项目结构

```
TraumChatRoom/
├── src/main/java/com/tc/traumchatroom/
│   ├── config/          # 配置类 (Security、WebSocket)
│   ├── controller/      # 控制器 (Auth、WebSocket、File)
│   ├── entity/          # 实体类 (User、Message)
│   ├── mapper/          # MyBatis 映射接口
│   ├── service/         # 业务逻辑层
│   ├── filter/          # JWT、日志过滤器
│   └── util/            # 工具类 (JWT、GuestName)
├── src/main/resources/
│   ├── static/          # 前端页面 (HTML、CSS、JS)
│   ├── templates/       # MyBatis XML 映射文件
│   └── application.yml  # 配置文件
└── uploads/             # 文件上传目录
```

---

## 🔐 认证说明

本项目采用 **JWT 无状态认证**：

1. 登录成功后返回 JWT Token
2. 客户端存储 Token (localStorage/sessionStorage)
3. 后续请求携带 `Authorization: Bearer <token>` 请求头
4. 服务端验证 Token 并设置 SecurityContext

JWT 配置 (application.yml)：
```yaml
jwt:
  secret: your_secret_key_here
  expiration: 604800000  # 7天有效期
```

---

## 📋 API 概览

### 认证接口
| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/login` | POST | 用户登录，返回 JWT |
| `/api/logout` | POST | 退出登录 |
| `/register` | POST | 用户注册 |

### 用户接口
| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/current-user` | GET | 获取当前用户 (含游客) |
| `/api/online-users` | GET | 获取在线用户列表 |
| `/api/update-profile` | POST | 更新个人资料 |

### 消息接口
| 接口 | 方法 | 说明 |
|------|------|------|
| `/history` | GET | 获取群聊历史 |
| `/api/private-history/{name}` | GET | 获取私聊历史 |
| `/ws` | WebSocket | WebSocket 连接端点 |

### 文件接口
| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/file/upload` | POST | 文件上传 |
| `/api/file/download/{name}` | GET | 文件下载 |

---

## 🌐 WebSocket 消息

| 目标路径 | 说明 |
|----------|------|
| `/app/space` | 发送群聊消息 |
| `/app/private.message` | 发送私聊消息 |
| `/app/heartbeat` | 心跳检测 |
| `/topic/messages` | 订阅群聊消息 |
| `/user/queue/private` | 订阅私聊消息 |

---

## ⚙️ 配置说明

### 文件上传配置
```yaml
file:
  upload-dir: uploads        # 上传目录
  max-file-size: 104857600   # 100MB
```

### 跨域配置
- 允许所有来源 (`*`)
- 支持携带凭证 (Cookie)
- 允许的 HTTP 方法: GET, POST, PUT, DELETE, OPTIONS

---

## 👨‍💻 作者

**TraumTC**

- GitHub: [@TraumTC](https://github.com/TraumTC)
- 项目: [TraumChatRoom](https://github.com/TraumTC/TraumChatRoom)

---

> 如果这个项目对你有帮助，欢迎 ⭐ Star 支持！
