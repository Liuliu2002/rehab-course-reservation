create database if not exists rehab_course default charset utf8mb4;
use rehab_course;

drop table if exists appointment;
drop table if exists teacher_schedule;
drop table if exists rehab_course;
drop table if exists student;
drop table if exists teacher;

create table teacher (
    id bigint primary key auto_increment,
    name varchar(32) not null,
    phone varchar(20) not null unique,
    password varchar(128) not null,
    specialty varchar(100),
    introduction varchar(500),
    status tinyint not null default 1,
    role varchar(16) not null default 'teacher',
    create_time datetime,
    update_time datetime
);

create table student (
    id bigint primary key auto_increment,
    name varchar(32) not null,
    phone varchar(20) not null unique,
    password varchar(128) not null,
    status tinyint not null default 1,
    create_time datetime
);

create table rehab_course (
    id bigint primary key auto_increment,
    name varchar(64) not null,
    category varchar(32) not null,
    suitable_crowd varchar(255),
    training_goal varchar(255),
    duration_minutes int not null,
    price decimal(10,2) not null,
    status tinyint not null default 1,
    create_time datetime,
    update_time datetime
);

create table teacher_schedule (
    id bigint primary key auto_increment,
    teacher_id bigint not null,
    start_time datetime not null,
    end_time datetime not null,
    status tinyint not null comment '1 available, 2 occupied, 3 disabled',
    create_time datetime,
    index idx_teacher_time(teacher_id, start_time, end_time),
    constraint fk_schedule_teacher foreign key (teacher_id) references teacher(id)
);

create table appointment (
    id bigint primary key,
    appointment_no varchar(32) not null unique,
    student_id bigint not null,
    teacher_id bigint not null,
    course_id bigint not null,
    schedule_id bigint not null,
    status tinyint not null comment '1 pending, 2 confirmed, 3 completed, 4 cancelled, 5 rejected',
    amount decimal(10,2) not null,
    cancel_reason varchar(255),
    create_time datetime,
    update_time datetime,
    index idx_student(student_id),
    index idx_teacher(teacher_id),
    index idx_schedule(schedule_id),
    constraint fk_appointment_student foreign key (student_id) references student(id),
    constraint fk_appointment_teacher foreign key (teacher_id) references teacher(id),
    constraint fk_appointment_course foreign key (course_id) references rehab_course(id),
    constraint fk_appointment_schedule foreign key (schedule_id) references teacher_schedule(id)
);

insert into teacher(name, phone, password, specialty, introduction, status, role, create_time, update_time)
values
('李明远', '13800000001', 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=', '肩颈康复、运动损伤', '国家认证康复治疗师，擅长久坐人群肩颈疼痛和常见运动损伤的功能恢复。', 1, 'admin', now(), now()),
('王静', '13800000002', 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=', '膝关节康复、术后恢复', '专注膝关节活动度、下肢力量和关节置换术后康复训练。', 1, 'teacher', now(), now()),
('张伟', '13800000003', 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=', '腰背疼痛、核心训练', '擅长慢性腰痛评估、核心稳定训练和日常姿势纠正。', 1, 'teacher', now(), now()),
('陈雨欣', '13800000004', 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=', '神经康复、平衡训练', '具有神经系统康复经验，侧重脑卒中后平衡、步态和日常活动能力训练。', 1, 'teacher', now(), now()),
('刘志强', '13800000005', 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=', '踝关节康复、体能恢复', '擅长踝关节扭伤及术后恢复，并提供循序渐进的运动能力重建方案。', 1, 'teacher', now(), now()),
('赵敏', '13800000006', 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=', '老年康复、产后康复', '关注老年防跌倒、基础体能改善及女性产后核心功能恢复。', 1, 'teacher', now(), now());

insert into student(name, phone, password, status, create_time)
values
('王小雨', '13900000001', 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=', 1, now()),
('陈晨', '13900000002', 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=', 1, now()),
('刘思远', '13900000003', 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=', 1, now()),
('赵可欣', '13900000004', 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=', 1, now()),
('周子涵', '13900000005', 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=', 1, now()),
('孙晓琳', '13900000006', 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=', 1, now());

insert into rehab_course(name, category, suitable_crowd, training_goal, duration_minutes, price, status, create_time, update_time)
values
('肩颈舒缓训练', '肩颈康复', '久坐、伏案工作及肩颈紧张人群', '改善颈肩活动度，缓解肌肉紧张和酸痛', 45, 99.00, 1, now(), now()),
('膝关节稳定训练', '膝关节康复', '膝关节疼痛或术后恢复人群', '提升下肢力量和膝关节稳定性', 60, 129.00, 1, now(), now()),
('腰背核心激活', '脊柱康复', '慢性腰背不适及核心力量不足人群', '激活深层核心肌群，改善腰椎稳定性', 50, 109.00, 1, now(), now()),
('踝关节术后恢复', '踝关节康复', '踝关节扭伤或术后恢复人群', '恢复关节活动度、本体感觉和负重能力', 60, 139.00, 1, now(), now()),
('脑卒中平衡训练', '神经康复', '脑卒中恢复期且存在平衡障碍的人群', '改善坐站平衡、重心转移和步行能力', 60, 159.00, 1, now(), now()),
('老年防跌倒训练', '老年康复', '平衡能力下降或有跌倒风险的老年人', '增强下肢力量、反应能力和动态平衡', 45, 89.00, 1, now(), now()),
('产后核心与盆底恢复', '产后康复', '产后核心无力及盆底功能需要恢复的人群', '改善核心控制和盆底肌协调能力', 50, 149.00, 1, now(), now()),
('运动损伤功能恢复', '运动康复', '运动拉伤、扭伤恢复期人群', '恢复关节功能并逐步重建运动能力', 75, 189.00, 1, now(), now()),
('手腕灵活度训练', '上肢康复', '腕关节损伤或长期使用电脑的人群', '改善手腕活动度、握力和精细控制能力', 40, 79.00, 1, now(), now()),
('呼吸与耐力提升', '心肺康复', '体能下降或康复后期需要耐力训练的人群', '改善呼吸模式并逐步提升心肺耐力', 45, 119.00, 1, now(), now());

insert into teacher_schedule(teacher_id, start_time, end_time, status, create_time)
values
(1, timestamp(date_add(curdate(), interval 1 day), '09:00:00'), timestamp(date_add(curdate(), interval 1 day), '09:45:00'), 1, now()),
(1, timestamp(date_add(curdate(), interval 2 day), '14:00:00'), timestamp(date_add(curdate(), interval 2 day), '14:45:00'), 1, now()),
(1, timestamp(date_add(curdate(), interval 4 day), '10:00:00'), timestamp(date_add(curdate(), interval 4 day), '10:45:00'), 1, now()),
(1, timestamp(date_add(curdate(), interval 6 day), '15:00:00'), timestamp(date_add(curdate(), interval 6 day), '15:45:00'), 1, now()),
(2, timestamp(date_add(curdate(), interval 1 day), '10:00:00'), timestamp(date_add(curdate(), interval 1 day), '11:00:00'), 1, now()),
(2, timestamp(date_add(curdate(), interval 3 day), '09:00:00'), timestamp(date_add(curdate(), interval 3 day), '10:00:00'), 1, now()),
(2, timestamp(date_add(curdate(), interval 5 day), '14:00:00'), timestamp(date_add(curdate(), interval 5 day), '15:00:00'), 1, now()),
(2, timestamp(date_add(curdate(), interval 7 day), '10:00:00'), timestamp(date_add(curdate(), interval 7 day), '11:00:00'), 1, now()),
(3, timestamp(date_add(curdate(), interval 2 day), '09:00:00'), timestamp(date_add(curdate(), interval 2 day), '09:50:00'), 1, now()),
(3, timestamp(date_add(curdate(), interval 3 day), '15:00:00'), timestamp(date_add(curdate(), interval 3 day), '15:50:00'), 1, now()),
(3, timestamp(date_add(curdate(), interval 6 day), '10:00:00'), timestamp(date_add(curdate(), interval 6 day), '10:50:00'), 1, now()),
(3, timestamp(date_add(curdate(), interval 8 day), '14:00:00'), timestamp(date_add(curdate(), interval 8 day), '14:50:00'), 1, now()),
(4, timestamp(date_add(curdate(), interval 1 day), '14:00:00'), timestamp(date_add(curdate(), interval 1 day), '15:00:00'), 1, now()),
(4, timestamp(date_add(curdate(), interval 4 day), '09:00:00'), timestamp(date_add(curdate(), interval 4 day), '10:00:00'), 1, now()),
(4, timestamp(date_add(curdate(), interval 6 day), '14:00:00'), timestamp(date_add(curdate(), interval 6 day), '15:00:00'), 1, now()),
(4, timestamp(date_add(curdate(), interval 9 day), '10:00:00'), timestamp(date_add(curdate(), interval 9 day), '11:00:00'), 1, now()),
(5, timestamp(date_add(curdate(), interval 2 day), '10:00:00'), timestamp(date_add(curdate(), interval 2 day), '11:00:00'), 1, now()),
(5, timestamp(date_add(curdate(), interval 4 day), '15:00:00'), timestamp(date_add(curdate(), interval 4 day), '16:00:00'), 1, now()),
(5, timestamp(date_add(curdate(), interval 7 day), '09:00:00'), timestamp(date_add(curdate(), interval 7 day), '10:00:00'), 1, now()),
(5, timestamp(date_add(curdate(), interval 10 day), '14:00:00'), timestamp(date_add(curdate(), interval 10 day), '15:00:00'), 1, now()),
(6, timestamp(date_add(curdate(), interval 3 day), '10:00:00'), timestamp(date_add(curdate(), interval 3 day), '10:50:00'), 1, now()),
(6, timestamp(date_add(curdate(), interval 5 day), '15:00:00'), timestamp(date_add(curdate(), interval 5 day), '15:50:00'), 1, now()),
(6, timestamp(date_add(curdate(), interval 8 day), '09:00:00'), timestamp(date_add(curdate(), interval 8 day), '09:50:00'), 1, now()),
(6, timestamp(date_add(curdate(), interval 10 day), '10:00:00'), timestamp(date_add(curdate(), interval 10 day), '10:50:00'), 1, now());
