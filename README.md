# 康复课程预约系统

基于 `sky-take-out` 的多模块结构重新创建的新项目，原项目代码没有被修改。

## 项目结构

```text
rehab-course-reservation
├── rehab-common   公共返回结果、JWT、线程上下文
├── rehab-pojo     实体、DTO、VO
├── rehab-server   Controller、Service、Mapper、WebSocket、定时任务
└── sql            建表脚本和初始化数据
```

## 已实现功能

- 教师端登录、教师创建
- 康复课程发布、上下架、查询
- 教师可预约时间段发布
- 学生登录，首次登录自动创建学生账号
- 学生浏览课程、查看教师可预约时间
- 学生提交预约、取消预约
- 教师查看预约、确认预约、拒绝预约、完成课程
- Redis 缓存课程详情、课程列表、教师列表、教师可预约时间段，并使用空值缓存缓解缓存穿透
- Redis + Lua 原子校验并占用热门教师时间段，防止高并发预约超约
- RabbitMQ 异步创建预约记录，通过发布确认、消费重试和死信队列提高可靠性
- WebSocket 推送新预约消息
- 定时任务自动取消 30 分钟未确认的预约
- MyBatis + MySQL 持久化

## 核心业务状态

课程状态：

```text
1 上架
0 下架
```

排课状态：

```text
1 可预约
2 已占用
3 停用
```

预约状态：

```text
1 待确认
2 已确认
3 已完成
4 已取消
5 已拒绝
```

## 启动方式

1. 执行 `sql/schema.sql` 创建数据库和测试数据。
   如果数据库已经存在，只需要执行 `sql/seed_cn_data.sql` 更新并补充中文示例数据。
2. 启动 RabbitMQ，并根据 `.env.example` 配置 MySQL、Redis、RabbitMQ 和 JWT 环境变量。本地可执行：

   ```bash
   docker compose -f deploy/docker-compose.rabbitmq.yml up -d
   ```

   管理后台默认为 `http://localhost:15672`，开发环境默认账号和密码均为 `guest`。
3. 在项目根目录执行：

```bash
mvn clean package
```

4. 启动服务：

```bash
mvn -pl rehab-server spring-boot:run
```

## 中文示例数据

- 6 名康复教师，其中手机号 `13800000001` 为管理员账号。
- 6 名示例学员，手机号为 `13900000001` 至 `13900000006`。
- 10 门中文康复课程和 24 个未来可预约时段。
- 所有示例账号的测试密码均为 `123456`，仅用于本地开发。

## 双击启动前端（Windows）

1. 先在 IDEA 中启动 `RehabApplication`，确保后端监听 `8080`。
2. 双击项目根目录的 `start-frontend.bat`。
3. 脚本会复用 `D:\develop\back\TakeOut\nginx-1.20.2\nginx.exe`，并自动打开 `http://localhost:8090/`。
4. 需要关闭前端服务时，双击 `stop-frontend.bat`。

这套配置使用独立的 `8090` 端口和 PID，不会修改或停止 TakeOut 项目的 Nginx 配置。

## 简历亮点写法

- 基于 Spring Boot + MyBatis 设计并实现康复课程预约系统，覆盖教师排课、学生预约、预约确认、取消、完成等核心流程。
- 使用 Redis 缓存课程详情、教师信息和可预约时间段，并通过空值缓存缓解缓存穿透问题。
- 基于 Redis + Lua 原子完成时间段名额校验与扣减，解决高并发预约下的重复占用问题。
- 使用 RabbitMQ 对预约成功请求进行异步落库，通过发布确认、手动 ACK、消费重试和死信队列实现削峰与可靠投递。
- 使用 15 分钟无状态 Access Token + Redis 中的 7 天轮换 Refresh Token 实现双端身份认证；
  Refresh Token 仅通过 HttpOnly Cookie 传输，退出登录或禁用账号后不能继续刷新 Access Token。
- 使用事务保证预约创建与时间段占用状态更新的一致性。
- 使用 WebSocket 实现新预约实时通知，提升教师端响应效率。
- 使用 Spring Task 定时取消超时未确认预约，保证预约状态自动流转。

## 登录会话配置

默认 Access Token 有效期为 15 分钟，Refresh Token/Redis 会话的空闲期限为 7 天，绝对最长
会话期限为 30 天。前端在接口返回 401 时通过共享 Promise 发起一次刷新，刷新成功后 Refresh Token
同时轮换。每次刷新可以延长 7 天空闲期限，但绝不会超过登录时确定的 30 天绝对期限。
普通业务请求只验证 JWT，不查询 Redis；注销或禁用账号后，已经签发的 Access Token 最多仍可使用
15 分钟，但 Refresh Token 会立即失效，无法继续续期。
以下环境变量可以调整：

```text
REHAB_ACCESS_TOKEN_TTL_MINUTES=15
REHAB_REFRESH_TOKEN_TTL_DAYS=7
REHAB_MAX_SESSION_TTL_DAYS=30
REHAB_REFRESH_COOKIE_SECURE=false
```

生产环境通过 HTTPS 部署时，必须将 `REHAB_REFRESH_COOKIE_SECURE` 设置为 `true`。
