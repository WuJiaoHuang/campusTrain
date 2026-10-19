# Stage 11：OSS 私有视频

## 流程和配置

training-service 仅签发短期地址和维护元数据，不接收或转发视频字节。客户端先调用 `POST /videos/upload-url`，使用返回的 URL 直接向 OSS 发送 PUT，且 `Content-Type` 必须与申请时一致；完成后调用 `PUT /lessons/{id}/video`，服务通过 OSS HEAD 验证对象、大小和类型后更新课时。播放时 `GET /lessons/{id}/video/play-url` 动态签发短期 GET 地址。网关要求 JWT，地址不要写入数据库或日志。

Bucket 必须是 Private；本地默认北京 `https://oss-cn-beijing.aliyuncs.com`、`cn-beijing` 和 `java-ai-wujiaohuang`。`OSS_ENDPOINT`、`OSS_REGION`、`OSS_BUCKET`、`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET` 可通过环境变量配置；本地开发也可写入项目根目录被 Git 忽略的 `.env`。切勿将真实凭据写入 Git。非敏感默认值位于 `config/nacos/training-service-dev.yaml` 和本地配置，Nacos 使用 Stage 8 的 `campustrain-dev` / `CAMPUS_TRAIN`。上传和播放地址默认 900 / 600 秒，最大视频大小默认 500 MiB。

浏览器跨域直传还需要管理员在 OSS 为可信前端来源配置 CORS，允许 PUT、GET 和 `Content-Type`；本次没有修改 Bucket CORS，也没有进行浏览器跨域验收。

ObjectKey 由服务端生成 `campustrain/videos/yyyy/MM/{uuid}.{mp4|webm|mov}`，只使用文件名扩展名、不使用原始路径。数据库保存 key、字节大小、Content-Type 和时长，避免保存过期 URL。普通课时 CRUD 不接受视频字段，请使用视频绑定接口。已有绑定的视频课时不能直接改为图文类型。

## 数据库与一致性

先检查 `campus_training.lesson` 是否已有 `video_size` 和 `video_content_type`，不存在时手动执行 `training-service/sql/stage11-oss.sql`；SQL 文件放入项目不会自动执行。`stage11-test-data.sql` 只供创建独立验收层级，每次执行会创建新记录，应记录输出 ID 并只清理这些记录。

MySQL 和 OSS 不是同一个事务：替换时先验证新对象，数据库提交后尽力删除旧对象；删除课时也是数据库提交后尽力删除视频。删除失败只记录错误，不回滚已提交的数据库，可能产生 orphan object，本阶段不做异步补偿。真实生产应限制上传、绑定和删除操作只能由授权教师或管理员执行，本阶段未实现完整 RBAC。预签名 PUT 在到期前可重复使用，且申请时声明的大小不由 OSS 在上传前强制限制；当前仅在绑定时检验实际大小，不应把它当作生产级配额或一次性上传凭证。

## 验收

真实验收必须先检查 Bucket ACL 为 private；公开桶不得上传测试视频。设置环境变量后启动 training-service，通过 API 获取上传 URL，客户端直接 PUT 到 OSS 域名，然后绑定并查询数据库 metadata；使用播放 URL 下载并与原文件校验。使用同一专用课时替换并确认旧对象消失，再删除课时并确认新对象消失。还应验证非法类型、大小、key、缺失对象和未绑定播放等失败路径。

### 2026-10-19 实测记录

- 通过 OSS GetBucketAcl 确认 Bucket 为 `private`，未修改云端 ACL。
- 本机原始视频 `D:\Huawei Share\Screenshot\20260315_184627.mp4`，841000 字节，`video/mp4`；原文件未移动、修改或复制到仓库。
- 使用 Nacos 和 MySQL 的 training-service 8082，SQL 已执行并确认新增字段。专用 training/course/chapter/lesson ID 分别为 230/307/1072/3673，验收后均已清理；额外失败测试课时 3674 也已清理。
- 客户端直接 PUT 到 Bucket OSS 域名，首次 key `campustrain/videos/2026/10/61403434abf04e639c0f65af0a9bc46c.mp4`，PUT 200；绑定后数据库记录大小、类型、时长正确。签名 GET 下载的 SHA-256 与本地文件一致，未签名 GET 为 403。
- 用同一视频重新直传 `campustrain/videos/2026/10/33c35721c5ad4f4bb4404ca78b1785fa.mp4` 并绑定：数据库指向新 key，旧 key 的签名 GET 为 404；删除课时后数据库记录不存在，新 key 的签名 GET 也为 404。两次验收对象均已清理。
- 非法类型、零字节、超限、错误 key 前缀、不存在对象、未绑定播放均返回业务 code 400；现有 training-service 业务异常沿用统一 Result，HTTP 状态不因此改动。
- 临时启动 Gateway 与 training-service 并连接真实 Nacos：无 Token 请求视频上传授权返回 HTTP 401；使用测试 JWT 后经网关获取签名 PUT URL 及查询课时均返回 code 200，响应携带 TraceId。该次只获取授权，没有上传第三个对象，测试进程已停止。

本次真实 OSS 主要链路已验证。Bucket 可能存在单个对象覆盖 ACL 的情况，生产仍需审查对象权限、Bucket Policy 和访问授权；示例并非完整生产授权方案。
