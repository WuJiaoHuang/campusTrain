# CampusTrain

CampusTrain 是一个基于 Spring Boot、Spring Cloud Alibaba 和 MyBatis-Plus 的校园培训业务项目。

## training-service 内容结构

```text
Training
 └── Course
      └── Chapter
           └── Lesson
```

## 阶段 4：课程、章节、课时内容模型

training-service 在阶段 4 新增课程、章节、课时与培训课程目录接口。

### 课程管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/courses` | 创建课程 |
| PUT | `/courses` | 修改课程 |
| GET | `/courses/{id}` | 查询课程详情 |
| GET | `/courses/training/{trainingId}` | 查询培训下课程 |
| DELETE | `/courses/{id}` | 删除课程 |

### 章节管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/chapters` | 创建章节 |
| PUT | `/chapters` | 修改章节 |
| GET | `/chapters/course/{courseId}` | 查询课程下章节 |
| PUT | `/chapters/sort` | 章节排序 |
| DELETE | `/chapters/{id}` | 删除章节 |

### 课时管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/lessons` | 创建课时 |
| PUT | `/lessons` | 修改课时 |
| GET | `/lessons/chapter/{chapterId}` | 查询章节下课时 |
| PUT | `/lessons/sort` | 课时排序 |
| DELETE | `/lessons/{id}` | 删除课时 |

### 培训课程目录

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/trainings/{trainingId}/catalog` | 查询培训课程目录 |

目录接口按 Course、Chapter、Lesson 三次批量查询组装树形结构，避免在循环中查询数据库。

## 接口文档

training-service 启动后可访问：

- Knife4j：`http://localhost:8082/doc.html`
- OpenAPI JSON：`http://localhost:8082/v3/api-docs`

## 阶段 5：User Service、Auth Service 与 JWT

阶段 5 建立基础身份体系，包含用户资料、角色、BCrypt 密码哈希、Spring Security 登录认证、JWT 签发与解析。

### 服务职责

- `user-service`：维护 `sys_user` 用户资料，负责用户 CRUD。创建用户时接收明文密码，但只保存 BCrypt 哈希；查询、分页、详情接口不会返回 `passwordHash`。
- `auth-service`：负责登录认证、JWT 签发和阶段 5 自测用的 Token 解析。由于阶段 6 才引入 OpenFeign，本阶段 `auth-service` 临时直连 `campus_user` 数据库读取 `sys_user`，用于完成身份认证闭环。后续跨服务调用再按服务边界演进。
- `campus-gateway`：阶段 5 不做 JWT Filter，统一网关鉴权留到阶段 7。

### 登录流程

```mermaid
flowchart TD
    Client[Client] --> Login[POST /auth/login]
    Login --> Manager[AuthenticationManager]
    Manager --> Token[UsernamePasswordAuthenticationToken]
    Token --> UserDetails[UserDetailsService]
    UserDetails --> DB[(sys_user)]
    DB --> Password[BCrypt PasswordEncoder]
    Password --> Auth[Authentication]
    Auth --> JWT[JWT]
    JWT --> Client
```

### 用户接口

| 服务 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| user-service | POST | `/users` | 创建用户，密码写入 BCrypt 哈希 |
| user-service | GET | `/users/{id}` | 查询用户详情 |
| user-service | GET | `/users/page` | 分页查询用户 |
| user-service | PUT | `/users` | 修改 nickname、role、status |
| user-service | DELETE | `/users/{id}` | 删除用户 |

### 认证接口

| 服务 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| auth-service | POST | `/auth/login` | 登录并签发 Access Token |
| auth-service | GET | `/auth/token-info` | 解析 Bearer Token，返回 userId、username、role |

### 开发测试账号

阶段 5 初始化 SQL 会写入以下开发账号，密码均为 `123456`，数据库中只保存 BCrypt 哈希。

| username | role |
| --- | --- |
| `student01` | `STUDENT` |
| `teacher01` | `TEACHER` |
| `admin01` | `ADMIN` |

这些账号仅用于开发环境。

### 密码与 Token 策略

- 密码不能明文存储，因为数据库泄露时明文密码会直接暴露用户凭据；当前使用 Spring Security `BCryptPasswordEncoder` 生成 `password_hash`。
- JWT 使用 HMAC SHA 签名，Secret 通过 `jwt.secret` 配置，默认值仅用于开发；生产环境必须使用 `JWT_SECRET` 环境变量。
- JWT Claims 包含：`sub`/username、`userId`、`role`、`iat`、`exp`。
- Access Token 默认有效期为 2 小时，即 `7200000 ms`，接口返回的 `expiresIn` 单位是秒。
- 阶段 5 不实现 Refresh Token。Access Token 到期后重新登录。
- 登录请求中会设置当前请求的 `SecurityContext`，但 JWT 架构下后续请求不能依赖内存 Context 长期保存身份，后续请求必须通过 Token 重建身份。
