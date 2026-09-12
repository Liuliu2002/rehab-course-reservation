use rehab_course;

-- Run once when upgrading a database created by the original schema.
alter table teacher
    add column role varchar(16) not null default 'teacher' after status;

-- Preserve access to the original demo accounts without retaining plaintext passwords.
update teacher
set password = 'sha256$AQIDBAUGBwgJCgsMDQ4PEA==$oBRtYRvZ0r29ANGqBrzkgFAPzmB5DguRbZle9eo0saM='
where password = '123456';

update teacher set role = 'admin' where id = 1;

alter table teacher_schedule
    add constraint fk_schedule_teacher
        foreign key (teacher_id) references teacher(id);

alter table appointment
    add constraint fk_appointment_student
        foreign key (student_id) references student(id),
    add constraint fk_appointment_teacher
        foreign key (teacher_id) references teacher(id),
    add constraint fk_appointment_course
        foreign key (course_id) references rehab_course(id),
    add constraint fk_appointment_schedule
        foreign key (schedule_id) references teacher_schedule(id);
