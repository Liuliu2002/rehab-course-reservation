package com.rehab.service.impl;

import com.rehab.common.BaseContext;
import com.rehab.config.BusinessException;
import com.rehab.config.RabbitMqConfig;
import com.rehab.mapper.AppointmentMapper;
import com.rehab.mapper.CourseMapper;
import com.rehab.mapper.ScheduleMapper;
import com.rehab.mq.AppointmentMessage;
import com.rehab.pojo.dto.AppointmentDecisionDTO;
import com.rehab.pojo.dto.AppointmentSubmitDTO;
import com.rehab.pojo.entity.Appointment;
import com.rehab.pojo.entity.RehabCourse;
import com.rehab.pojo.entity.TeacherSchedule;
import com.rehab.pojo.vo.AppointmentSubmitVO;
import com.rehab.pojo.vo.AppointmentDetailVO;
import com.rehab.service.AppointmentService;
import com.rehab.utils.RedisCacheClient;
import com.rehab.utils.RedisIdWorker;
import com.rehab.utils.RedisKeys;
import com.rehab.websocket.AppointmentWebSocket;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 预约领域服务，负责预约入口校验、Redis 原子抢占以及预约状态流转。
 *
 * <p>面试讲解主线：</p>
 * <ol>
 *     <li>MySQL 校验课程和排班的业务有效性；</li>
 *     <li>Redis Lua 原子扣减单时段名额；</li>
 *     <li>向 RabbitMQ 发送持久化预约消息，由后台消费者异步落库；</li>
 *     <li>确认、拒绝、取消、完成均使用数据库条件更新，避免并发覆盖状态。</li>
 * </ol>
 *
 * <p>设计边界：Redis、RabbitMQ 与 MySQL 不属于同一个事务，目前采用发布确认、失败回补和最终一致性；生产环境可进一步
 * 使用事务 Outbox、补偿任务和提交幂等键收敛异常窗口。</p>
 */
@Service
public class AppointmentServiceImpl implements AppointmentService {

    /**
     * Redis Lua脚本对象：抢课脚本在 Redis 内原子校验并扣减库存，避免并发预约时出现超卖。
     * 静态块加载classpath下的lua脚本，只初始化一次。
     */
    private static final DefaultRedisScript<Long> APPOINTMENT_SCRIPT;

    static {
        APPOINTMENT_SCRIPT = new DefaultRedisScript<>();
        // 加载lua脚本文件
        APPOINTMENT_SCRIPT.setLocation(new ClassPathResource("appointment.lua"));
        // 指定脚本返回值类型
        APPOINTMENT_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private AppointmentMapper appointmentMapper;

    @Autowired
    private ScheduleMapper scheduleMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Redis分布式ID生成器，用于生成预约主键id
     */
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private RedisCacheClient redisCacheClient;

    /**
     * RabbitMQ消息操作模板，用于发送预约异步消息
     */
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 提交预约。这里不直接插入 appointment 表，目的是让高峰请求先进入 Redis，
     * 再由 RabbitMQ 消费者按照数据库承载能力异步落库，实现削峰填谷。
     *
     * @param submitDTO 预约提交入参
     * @return AppointmentSubmitVO 返回预约id、预约单号以及当前处理状态PROCESSING
     */
    @Override
    public AppointmentSubmitVO submit(AppointmentSubmitDTO submitDTO) {
        // 1. 数据库基础业务校验：排班是否存在、是否启用可预约
        TeacherSchedule schedule = scheduleMapper.getById(submitDTO.getScheduleId());
        if (schedule == null || schedule.getStatus() != 1) {
            throw BusinessException.conflict("schedule is not available");
        }
        // 校验传入教师id和排班绑定教师是否一致
        if (!schedule.getTeacherId().equals(submitDTO.getTeacherId())) {
            throw BusinessException.badRequest("teacher and schedule do not match");
        }

        // 校验课程是否存在且启用
        RehabCourse course = courseMapper.getById(submitDTO.getCourseId());
        if (course == null || course.getStatus() != 1) {
            throw BusinessException.badRequest("course does not exist or is disabled");
        }

        // 生成预约唯一ID
        Long appointmentId = redisIdWorker.nextId("appointment");
        // 生成预约业务单号
        String appointmentNo = createAppointmentNo();
        LocalDateTime createTime = LocalDateTime.now();

        // 初始化该排班在Redis中的预约库存，不存在才设置，防止覆盖
        initScheduleStock(schedule);

        // 2. 执行Lua脚本，原子抢占名额。返回0代表抢占成功；非0代表库存不足/已被预约
        Long result = stringRedisTemplate.execute(
                APPOINTMENT_SCRIPT,
                Collections.singletonList(RedisKeys.APPOINTMENT_STOCK_KEY + schedule.getId()));
        if (result == null || result != 0) {
            throw BusinessException.conflict("schedule has been reserved");
        }

        // 3. Redis写入预约归属人：记录哪个学生发起本次预约，用于后续状态查询鉴权
        setSubmitOwner(appointmentId, BaseContext.getCurrentId());
        // Redis写入预约临时状态：PROCESSING 处理中
        setSubmitStatus(appointmentId, "PROCESSING");

        // 组装MQ消息体，交给消费者异步落库写mysql
        AppointmentMessage message = new AppointmentMessage(
                appointmentId,
                appointmentNo,
                BaseContext.getCurrentId(),
                submitDTO.getTeacherId(),
                submitDTO.getCourseId(),
                submitDTO.getScheduleId(),
                course.getPrice(),
                createTime);

        try {
            // 发送预约消息到RabbitMQ，同步等待消息投递确认
            publishAppointment(message);
        } catch (Exception exception) {
            // MQ投递失败：回滚Redis库存，修改预约临时状态为FAILED，抛出异常
            restoreScheduleStock(schedule.getId(), schedule.getTeacherId());
            setSubmitStatus(appointmentId, "FAILED");
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "appointment queue is temporarily unavailable, please retry");
        }

        // 清除教师排班缓存，保证前端查询最新排班数据
        redisCacheClient.delete(RedisKeys.CACHE_SCHEDULE_KEY + schedule.getTeacherId());
        // WebSocket推送消息给对应教师，通知有新预约待处理
        AppointmentWebSocket.broadcastToTeacher(schedule.getTeacherId(), "new appointment processing: " + appointmentNo);

        // 返回预约信息，此时数据库记录还未生成，处于异步处理中
        return new AppointmentSubmitVO(appointmentId, appointmentNo, "PROCESSING");
    }

    /**
     * 查询异步提交结果。owner key 用于防止学生通过猜测预约 ID 查询他人的处理状态；
     * Redis 状态过期后，以 MySQL 是否已经存在预约记录作为兜底。
     *
     * @param appointmentId 预约ID
     * @return 预约状态：PROCESSING / SUCCESS / FAILED / UNKNOWN
     */
    @Override
    public String getSubmitStatus(Long appointmentId) {
        // 读取Redis保存的预约归属学生ID
        String owner = stringRedisTemplate.opsForValue().get(RedisKeys.APPOINTMENT_OWNER_KEY + appointmentId);
        // 如果Redis存在归属记录，校验是否是当前登录学生，不是则禁止访问
        if (owner != null && !owner.equals(BaseContext.getCurrentId().toString())) {
            throw BusinessException.forbidden("no permission to view this appointment");
        }

        Appointment persisted = null;
        // Redis归属记录已过期，兜底查询数据库校验权限
        if (owner == null) {
            persisted = appointmentMapper.getById(appointmentId);
            if (persisted == null || !persisted.getStudentId().equals(BaseContext.getCurrentId())) {
                throw BusinessException.forbidden("no permission to view this appointment");
            }
        }

        // 优先读取Redis临时状态（异步落库过程中的状态）
        String status = stringRedisTemplate.opsForValue().get(RedisKeys.APPOINTMENT_STATUS_KEY + appointmentId);
        if (status != null) {
            return status;
        }

        // Redis状态key过期，查询数据库预约记录判断最终状态
        Appointment appointment = persisted == null ? appointmentMapper.getById(appointmentId) : persisted;
        if (appointment != null && !appointment.getStudentId().equals(BaseContext.getCurrentId())) {
            throw BusinessException.forbidden("no permission to view this appointment");
        }
        // 数据库存在记录=成功；不存在=未知
        return appointment == null ? "UNKNOWN" : "SUCCESS";
    }

    /**
     * 教师确认预约：待确认状态 -> 已确认
     *
     * @param appointmentId 预约ID
     */
    @Override
    public void confirm(Long appointmentId) {
        // 获取预约并校验操作人是教师本人
        Appointment appointment = getTeacherAppointment(appointmentId);
        // CAS条件更新，仅当状态为待确认时更新为已确认
        changeStatus(appointmentId, Appointment.PENDING_CONFIRM, Appointment.CONFIRMED, null,
                "only pending appointments can be confirmed");
    }

    /**
     * 教师拒绝预约：待确认状态 -> 已拒绝。事务保证数据库状态变更+库存释放原子性
     *
     * @param decisionDTO 预约决策DTO，包含预约ID与拒绝原因
     */
    @Override
    @Transactional
    public void reject(AppointmentDecisionDTO decisionDTO) {
        Appointment appointment = getTeacherAppointment(decisionDTO.getAppointmentId());
        // CAS更新预约状态为拒绝
        changeStatus(decisionDTO.getAppointmentId(), Appointment.PENDING_CONFIRM, Appointment.REJECTED,
                decisionDTO.getReason(), "only pending appointments can be rejected");
        // 释放数据库排班占用标记
        scheduleMapper.releaseOccupied(appointment.getScheduleId());
        // 恢复Redis预约库存，排班可再次被预约
        restoreScheduleStock(appointment.getScheduleId(), appointment.getTeacherId());
    }

    /**
     * 学生取消预约：激活状态预约取消。事务保证状态变更+库存释放
     *
     * @param decisionDTO 预约决策DTO，包含预约ID、取消原因
     */
    @Override
    @Transactional
    public void cancel(AppointmentDecisionDTO decisionDTO) {
        // 获取预约并校验操作人是预约学生本人
        Appointment appointment = getStudentAppointment(decisionDTO.getAppointmentId());
        // 条件更新：仅当前状态允许取消时才更新记录，返回影响行数
        int updated = appointmentMapper.cancelIfActive(decisionDTO.getAppointmentId(), decisionDTO.getReason());
        if (updated != 1) {
            throw BusinessException.conflict("appointment cannot be cancelled in current status");
        }
        // 释放数据库排班占用标记
        scheduleMapper.releaseOccupied(appointment.getScheduleId());
        // 恢复Redis库存，排班可重新预约
        restoreScheduleStock(appointment.getScheduleId(), appointment.getTeacherId());
    }

    /**
     * 教师标记预约完成：已确认 -> 已完成
     *
     * @param appointmentId 预约ID
     */
    @Override
    public void complete(Long appointmentId) {
        Appointment appointment = getTeacherAppointment(appointmentId);
        // CAS更新：仅已确认状态可以变更为完成
        changeStatus(appointmentId, Appointment.CONFIRMED, Appointment.COMPLETED, null,
                "only confirmed appointments can be completed");
    }

    /**
     * 查询当前教师名下所有预约详情列表
     *
     * @return 预约详情VO列表
     */
    @Override
    public List<AppointmentDetailVO> listForTeacher() {
        return appointmentMapper.listDetailsByTeacher(BaseContext.getCurrentId());
    }

    /**
     * 查询当前学生名下所有预约详情列表
     *
     * @return 预约详情VO列表
     */
    @Override
    public List<AppointmentDetailVO> listForStudent() {
        return appointmentMapper.listDetailsByStudent(BaseContext.getCurrentId());
    }

    /**
     * CAS状态更新工具方法：条件更新预约状态
     * 把【判断旧状态 + 更新新状态】合并为一条SQL，防止并发操作覆盖状态。
     *
     * @param appointmentId 预约ID
     * @param expectedStatus 期望旧状态
     * @param status 目标新状态
     * @param reason 变更原因（拒绝/取消时填写）
     * @param conflictMessage 状态不匹配时抛出异常提示
     */
    private void changeStatus(Long appointmentId, Integer expectedStatus, Integer status,
                              String reason, String conflictMessage) {
        int updated = appointmentMapper.updateStatusIfCurrent(
                appointmentId, status, reason, expectedStatus);
        if (updated != 1) {
            throw BusinessException.conflict(conflictMessage);
        }
    }

    /**
     * 获取预约并校验：当前登录用户是预约对应的教师
     *
     * @param appointmentId 预约ID
     * @return 预约实体
     */
    private Appointment getTeacherAppointment(Long appointmentId) {
        Appointment appointment = getExistingAppointment(appointmentId);
        if (!appointment.getTeacherId().equals(BaseContext.getCurrentId())) {
            throw BusinessException.forbidden("no permission to operate this appointment");
        }
        return appointment;
    }

    /**
     * 获取预约并校验：当前登录用户是预约对应的学生
     *
     * @param appointmentId 预约ID
     * @return 预约实体
     */
    private Appointment getStudentAppointment(Long appointmentId) {
        Appointment appointment = getExistingAppointment(appointmentId);
        if (!appointment.getStudentId().equals(BaseContext.getCurrentId())) {
            throw BusinessException.forbidden("no permission to operate this appointment");
        }
        return appointment;
    }

    /**
     * 根据预约ID查询预约记录，不存在抛出异常
     *
     * @param appointmentId 预约ID
     * @return 预约实体
     */
    private Appointment getExistingAppointment(Long appointmentId) {
        Appointment appointment = appointmentMapper.getById(appointmentId);
        if (appointment == null) {
            throw BusinessException.badRequest("appointment does not exist");
        }
        return appointment;
    }

    /**
     * 生成预约业务单号：R + yyyyMMddHHmmss + 8位大写随机字符串
     *
     * @return 预约业务编号
     */
    private String createAppointmentNo() {
        String timePart = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "R" + timePart + randomPart;
    }

    /**
     * 初始化排班Redis预约库存：setIfAbsent，key不存在才写入。每个排班固定1个预约名额，有效期7天
     *
     * @param schedule 教师排班实体
     */
    private void initScheduleStock(TeacherSchedule schedule) {
        String key = RedisKeys.APPOINTMENT_STOCK_KEY + schedule.getId();
        stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 7, TimeUnit.DAYS);
    }

    /**
     * 恢复排班Redis预约库存，重置为1，过期时间7天；同时清除教师排班缓存
     * 用于预约拒绝、预约取消场景释放名额
     *
     * @param scheduleId 排班ID
     * @param teacherId 教师ID
     */
    private void restoreScheduleStock(Long scheduleId, Long teacherId) {
        stringRedisTemplate.opsForValue().set(RedisKeys.APPOINTMENT_STOCK_KEY + scheduleId, "1", 7, TimeUnit.DAYS);
        redisCacheClient.delete(RedisKeys.CACHE_SCHEDULE_KEY + teacherId);
    }

    /**
     * 发送MQ预约消息，同步等待消息发布确认，实现消息投递可靠性保障
     * 设置消息持久化；使用CorrelationData做消息确认回调
     *
     * @param message 预约MQ消息体
     * @throws Exception 消息投递失败、无ack时抛出异常
     */
    private void publishAppointment(AppointmentMessage message) throws Exception {
        // 消息关联数据，用于消息确认、消息回退
        CorrelationData correlationData = new CorrelationData(message.getAppointmentId().toString());
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.APPOINTMENT_EXCHANGE,
                RabbitMqConfig.APPOINTMENT_ROUTING_KEY,
                message,
                rabbitMessage -> {
                    // 设置消息持久化，RabbitMQ重启消息不丢失
                    rabbitMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    rabbitMessage.getMessageProperties().setMessageId(message.getAppointmentId().toString());
                    return rabbitMessage;
                },
                correlationData);
        // 阻塞等待5秒，获取发布确认结果
        CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
        // 判断Broker是否成功接收消息；未ack或者消息被退回则抛异常
        if (!confirm.isAck() || correlationData.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ did not accept appointment message: " + confirm.getReason());
        }
    }

    /**
     * Redis写入预约临时状态，设置过期时间
     *
     * @param appointmentId 预约ID
     * @param status 状态 PROCESSING / FAILED
     */
    private void setSubmitStatus(Long appointmentId, String status) {
        stringRedisTemplate.opsForValue().set(
                RedisKeys.APPOINTMENT_STATUS_KEY + appointmentId,
                status,
                RedisKeys.APPOINTMENT_STATUS_TTL_HOURS,
                TimeUnit.HOURS);
    }

    /**
     * Redis写入预约归属学生ID，用于鉴权查询预约状态；过期时间比状态key多1小时，兜底鉴权
     *
     * @param appointmentId 预约ID
     * @param studentId 当前学生ID
     */
    private void setSubmitOwner(Long appointmentId, Long studentId) {
        stringRedisTemplate.opsForValue().set(
                RedisKeys.APPOINTMENT_OWNER_KEY + appointmentId,
                studentId.toString(),
                RedisKeys.APPOINTMENT_STATUS_TTL_HOURS + 1,
                TimeUnit.HOURS);
    }
}
