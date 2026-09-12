# 康复课程预约系统面试导读

## 1. 项目一句话介绍

这是一个面向康复课程场景的预约系统，包含学生、教师和管理员三类角色。学生可以浏览课程、选择教师排班并提交预约；教师维护自己的排班并审核预约；管理员负责课程和教师账号管理。系统重点解决高峰预约时的并发扣库存、异步落库、状态竞争和预约通知问题。

## 2. 核心业务流程

### 学生预约流程

1. 学生登录后选择课程、教师和排班。
2. 后端先校验课程、教师和排班是否有效。
3. 使用 Redis + Lua 原子判断排班库存并扣减。
4. 扣减成功后把预约信息发送到 RabbitMQ，并等待 Broker 发布确认。
5. 接口立即返回 `PROCESSING`，前端轮询查询处理状态。
6. 后台消费者从 RabbitMQ 读取消息，写入 MySQL 预约表并手动 ACK。
7. 写入成功后状态变为 `SUCCESS`，失败则变为 `FAILED`。

### 教师审核流程

1. 教师登录后查看自己相关的预约。
2. 对待确认预约执行确认或拒绝。
3. 确认后预约进入已确认状态。
4. 拒绝或学生取消时，释放 MySQL 排班状态和 Redis 库存。
5. 课程结束后教师可以标记预约完成。

## 3. 面试重点讲法

### Redis + Lua 防超卖

可以这样回答：

> 热门排班预约时，如果多个请求同时进来，直接查数据库再更新容易出现并发超约。我把每个排班的名额放到 Redis 中，用 Lua 脚本原子完成库存判断和扣减。扣减成功后发送 RabbitMQ 消息；数据库消费者落库时还会执行 `status = 1` 条件更新，Redis 负责快速拦截，MySQL 负责最终正确性。

代码位置：

- `rehab-server/src/main/java/com/rehab/service/impl/AppointmentServiceImpl.java`
- `rehab-server/src/main/resources/appointment.lua`

### 为什么不校验同一学生重复预约

这个项目按需求允许同一学生重复选课，所以 Lua 脚本只检查课程剩余名额，不检查学生是否已经选过。面试时可以主动说明：如果业务改成“不允许重复预约”，可以在 Lua 中增加 `studentId + scheduleId` 的 Set 校验，或者在数据库加唯一索引。

### RabbitMQ 异步落库

可以这样回答：

> 预约接口完成校验和 Redis 原子扣库存后，将持久化消息发送到 RabbitMQ，不直接写数据库。生产者开启 Publisher Confirm 和 mandatory return；消费者异步创建预约，数据库事务提交后手动 ACK，并按预约 ID 保证幂等。消息连续失败 5 次后由主队列的死信配置路由到 DLQ，再执行状态更新与库存补偿。

代码位置：

- `rehab-server/src/main/java/com/rehab/config/RabbitMqConfig.java`
- `rehab-server/src/main/java/com/rehab/service/impl/AppointmentRabbitConsumer.java`

### 缓存和缓存穿透

可以这样回答：

> 课程详情、课程列表、教师列表和可预约排班属于读多写少数据，所以使用 Redis 缓存。查询不到的数据会缓存一个短 TTL 的空值，避免大量无效 ID 请求直接打到数据库，这就是缓存穿透处理。

代码位置：

- `rehab-server/src/main/java/com/rehab/utils/RedisCacheClient.java`
- `rehab-server/src/main/java/com/rehab/service/impl/CourseServiceImpl.java`
- `rehab-server/src/main/java/com/rehab/service/impl/TeacherServiceImpl.java`
- `rehab-server/src/main/java/com/rehab/service/impl/ScheduleServiceImpl.java`

### 短期无状态 JWT + 可撤销 Refresh Token + RBAC 三角色鉴权

可以这样回答：

> 登录后签发 15 分钟无状态 Access Token；Refresh Token 保存在 Redis，空闲期限为 7 天、绝对会话上限为 30 天，使用 HttpOnly Cookie 并在刷新时轮换。普通请求只验证 JWT 签名、有效期和角色，不查询 Redis，再把用户 ID 和角色放进 ThreadLocal。退出、密码升级或账号禁用时删除 Refresh Token，阻止继续续期；已签发的 Access Token 最多继续有效 15 分钟。管理员负责课程和教师管理，教师处理自己的排班和预约，学生只能访问学生端接口；业务层再校验预约归属，避免只做 URL 权限而产生越权。

代码位置：

- `rehab-server/src/main/java/com/rehab/interceptor/JwtInterceptor.java`
- `rehab-common/src/main/java/com/rehab/common/BaseContext.java`

### 预约状态机

预约状态包括：

- `1`：待确认
- `2`：已确认
- `3`：已完成
- `4`：已取消
- `5`：已拒绝

面试可以强调：不是简单 CRUD，而是围绕预约生命周期做了状态约束，例如只有待确认能被确认/拒绝，只有待确认或已确认能被学生取消。

状态更新采用带旧状态条件的 SQL，例如：

```sql
update appointment
set status = 2
where id = ? and status = 1;
```

可以这样回答：

> 如果代码先查询状态再更新，确认、取消和超时任务可能同时通过检查。我把旧状态作为 UPDATE 条件，并检查影响行数，相当于数据库层 CAS，因此并发操作最终只有一个能成功。

### 排班冲突为什么要锁教师行

可以这样回答：

> 单纯“先查询是否重叠，再插入排班”仍然存在并发窗口。两个请求可能同时查到没有冲突。我在事务中先 `SELECT ... FOR UPDATE` 锁定对应教师，再执行时间交集查询和插入，使同一教师的排班创建串行化，不同教师之间仍可并发。

### 密码为什么使用 BCrypt

可以这样回答：

> BCrypt 自带随机盐和可调计算成本，比普通 SHA-256 更适合存储密码。项目保留旧 SHA-256 校验只是为了兼容历史数据，用户成功登录后会立即升级为 BCrypt，数据库中不接受明文密码。

## 4. 如果面试官追问缺点

可以诚实回答：

- 当前消费者预取值为 1，可以部署多个实例横向扩容；吞吐量提高后应结合数据库容量调整并发消费者数和 prefetch。
- Redis、RabbitMQ 和 MySQL 之间仍属于最终一致性，不是分布式强事务；当前通过发布确认、失败回补、幂等、重试和死信降低风险，生产环境可进一步使用事务 Outbox 和对账任务。
- 死信队列仍需要告警、人工重放和容量监控，避免失败消息长期积压。
- 当前测试以单元测试为主，还应使用 Testcontainers 补充 MySQL、Redis、RabbitMQ 和 Lua 集成测试。
- WebSocket 通知目前适合演示，生产环境可在数据库提交成功后再推送，并增加断线重连和消息确认。

## 5. 简历表述建议

康复课程预约系统｜Spring Boot + MyBatis + MySQL + Redis + RabbitMQ + 原生 JavaScript

- 负责康复课程预约系统核心业务开发，包括课程管理、教师排班、学生预约、预约审核与取消等模块。
- 基于短期无状态 JWT、Redis 可撤销 Refresh Token 和 RBAC 实现管理员、教师、学生三角色鉴权，结合预约归属校验防止水平越权。
- 针对热门课程预约场景，使用 Redis + Lua 实现名额原子扣减，解决并发预约下的课程名额超卖问题。
- 引入 RabbitMQ 实现预约请求异步落库，通过 Publisher Confirm、手动 ACK、预约 ID 幂等、消费重试和死信队列提升消息可靠性。
- 基于 WebSocket 实现预约提醒实时推送，教师端可及时收到学生预约申请，提升预约处理效率。
