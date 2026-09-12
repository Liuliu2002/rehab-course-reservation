use rehab_course;
set names utf8mb4;

-- 可在现有数据库上执行。教师和学生按手机号更新，课程按名称判重。
-- 示例账号密码均为 123456，首次登录后会自动升级为 BCrypt。
set @demo_password = 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM=';

insert into teacher(name, phone, password, specialty, introduction, status, role, create_time, update_time)
values
('李明远', '13800000001', @demo_password, '肩颈康复、运动损伤', '国家认证康复治疗师，擅长久坐人群肩颈疼痛和常见运动损伤的功能恢复。', 1, 'admin', now(), now()),
('王静', '13800000002', @demo_password, '膝关节康复、术后恢复', '专注膝关节活动度、下肢力量和关节置换术后康复训练。', 1, 'teacher', now(), now()),
('张伟', '13800000003', @demo_password, '腰背疼痛、核心训练', '擅长慢性腰痛评估、核心稳定训练和日常姿势纠正。', 1, 'teacher', now(), now()),
('陈雨欣', '13800000004', @demo_password, '神经康复、平衡训练', '具有神经系统康复经验，侧重脑卒中后平衡、步态和日常活动能力训练。', 1, 'teacher', now(), now()),
('刘志强', '13800000005', @demo_password, '踝关节康复、体能恢复', '擅长踝关节扭伤及术后恢复，并提供循序渐进的运动能力重建方案。', 1, 'teacher', now(), now()),
('赵敏', '13800000006', @demo_password, '老年康复、产后康复', '关注老年防跌倒、基础体能改善及女性产后核心功能恢复。', 1, 'teacher', now(), now())
on duplicate key update
name = values(name), specialty = values(specialty), introduction = values(introduction),
status = values(status), role = values(role), update_time = now();

insert into student(name, phone, password, status, create_time)
values
('王小雨', '13900000001', @demo_password, 1, now()),
('陈晨', '13900000002', @demo_password, 1, now()),
('刘思远', '13900000003', @demo_password, 1, now()),
('赵可欣', '13900000004', @demo_password, 1, now()),
('周子涵', '13900000005', @demo_password, 1, now()),
('孙晓琳', '13900000006', @demo_password, 1, now())
on duplicate key update name = values(name), status = values(status);

-- 把旧版英文演示课程原地改为中文，保留原有课程 ID 和预约关联。
update rehab_course
set name = '肩颈舒缓训练', category = '肩颈康复',
    suitable_crowd = '久坐、伏案工作及肩颈紧张人群',
    training_goal = '改善颈肩活动度，缓解肌肉紧张和酸痛',
    duration_minutes = 45, price = 99.00, update_time = now()
where name = 'Neck and Shoulder Relief';

update rehab_course
set name = '膝关节稳定训练', category = '膝关节康复',
    suitable_crowd = '膝关节疼痛或术后恢复人群',
    training_goal = '提升下肢力量和膝关节稳定性',
    duration_minutes = 60, price = 129.00, update_time = now()
where name = 'Knee Stability Training';

insert into rehab_course(name, category, suitable_crowd, training_goal, duration_minutes, price, status, create_time, update_time)
select seed.name, seed.category, seed.suitable_crowd, seed.training_goal,
       seed.duration_minutes, seed.price, 1, now(), now()
from (
    select '肩颈舒缓训练' name, '肩颈康复' category, '久坐、伏案工作及肩颈紧张人群' suitable_crowd, '改善颈肩活动度，缓解肌肉紧张和酸痛' training_goal, 45 duration_minutes, 99.00 price
    union all select '膝关节稳定训练', '膝关节康复', '膝关节疼痛或术后恢复人群', '提升下肢力量和膝关节稳定性', 60, 129.00
    union all select '腰背核心激活', '脊柱康复', '慢性腰背不适及核心力量不足人群', '激活深层核心肌群，改善腰椎稳定性', 50, 109.00
    union all select '踝关节术后恢复', '踝关节康复', '踝关节扭伤或术后恢复人群', '恢复关节活动度、本体感觉和负重能力', 60, 139.00
    union all select '脑卒中平衡训练', '神经康复', '脑卒中恢复期且存在平衡障碍的人群', '改善坐站平衡、重心转移和步行能力', 60, 159.00
    union all select '老年防跌倒训练', '老年康复', '平衡能力下降或有跌倒风险的老年人', '增强下肢力量、反应能力和动态平衡', 45, 89.00
    union all select '产后核心与盆底恢复', '产后康复', '产后核心无力及盆底功能需要恢复的人群', '改善核心控制和盆底肌协调能力', 50, 149.00
    union all select '运动损伤功能恢复', '运动康复', '运动拉伤、扭伤恢复期人群', '恢复关节功能并逐步重建运动能力', 75, 189.00
    union all select '手腕灵活度训练', '上肢康复', '腕关节损伤或长期使用电脑的人群', '改善手腕活动度、握力和精细控制能力', 40, 79.00
    union all select '呼吸与耐力提升', '心肺康复', '体能下降或康复后期需要耐力训练的人群', '改善呼吸模式并逐步提升心肺耐力', 45, 119.00
) seed
left join rehab_course course_data on course_data.name = seed.name
where course_data.id is null;

-- 每位教师补充 4 个未来排班；同一天重复执行不会生成重复时段。
insert into teacher_schedule(teacher_id, start_time, end_time, status, create_time)
select teacher.id,
       timestamp(date_add(curdate(), interval seed.day_offset day), seed.start_clock),
       timestamp(date_add(curdate(), interval seed.day_offset day), seed.end_clock),
       1, now()
from (
    select '13800000001' phone, 1 day_offset, '09:00:00' start_clock, '09:45:00' end_clock
    union all select '13800000001', 2, '14:00:00', '14:45:00'
    union all select '13800000001', 4, '10:00:00', '10:45:00'
    union all select '13800000001', 6, '15:00:00', '15:45:00'
    union all select '13800000002', 1, '10:00:00', '11:00:00'
    union all select '13800000002', 3, '09:00:00', '10:00:00'
    union all select '13800000002', 5, '14:00:00', '15:00:00'
    union all select '13800000002', 7, '10:00:00', '11:00:00'
    union all select '13800000003', 2, '09:00:00', '09:50:00'
    union all select '13800000003', 3, '15:00:00', '15:50:00'
    union all select '13800000003', 6, '10:00:00', '10:50:00'
    union all select '13800000003', 8, '14:00:00', '14:50:00'
    union all select '13800000004', 1, '14:00:00', '15:00:00'
    union all select '13800000004', 4, '09:00:00', '10:00:00'
    union all select '13800000004', 6, '14:00:00', '15:00:00'
    union all select '13800000004', 9, '10:00:00', '11:00:00'
    union all select '13800000005', 2, '10:00:00', '11:00:00'
    union all select '13800000005', 4, '15:00:00', '16:00:00'
    union all select '13800000005', 7, '09:00:00', '10:00:00'
    union all select '13800000005', 10, '14:00:00', '15:00:00'
    union all select '13800000006', 3, '10:00:00', '10:50:00'
    union all select '13800000006', 5, '15:00:00', '15:50:00'
    union all select '13800000006', 8, '09:00:00', '09:50:00'
    union all select '13800000006', 10, '10:00:00', '10:50:00'
) seed
join teacher on teacher.phone = seed.phone
left join teacher_schedule schedule_data
       on schedule_data.teacher_id = teacher.id
      and schedule_data.start_time = timestamp(date_add(curdate(), interval seed.day_offset day), seed.start_clock)
where schedule_data.id is null;

select '教师' data_type, count(*) data_count from teacher
union all select '学生', count(*) from student
union all select '课程', count(*) from rehab_course
union all select '未来可预约时段', count(*) from teacher_schedule where status = 1 and start_time > now();
