package com.rehab.mapper;

import com.rehab.pojo.entity.Appointment;
import com.rehab.pojo.vo.AppointmentDetailVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppointmentMapper {
    @Insert("insert into appointment(id,appointment_no,student_id,teacher_id,course_id,schedule_id,status,amount,cancel_reason,create_time,update_time) " +
            "values(#{id},#{appointmentNo},#{studentId},#{teacherId},#{courseId},#{scheduleId},#{status},#{amount},#{cancelReason},#{createTime},#{updateTime})")
    void insert(Appointment appointment);

    @Select("select * from appointment where id = #{id}")
    Appointment getById(Long id);

    @Select("select * from appointment where teacher_id = #{teacherId} order by create_time desc")
    List<Appointment> listByTeacher(Long teacherId);

    @Select("select * from appointment where student_id = #{studentId} order by create_time desc")
    List<Appointment> listByStudent(Long studentId);

    @Select("select a.*, st.name as student_name, t.name as teacher_name, " +
            "c.name as course_name, c.category as course_category, " +
            "s.start_time, s.end_time " +
            "from appointment a " +
            "join student st on st.id = a.student_id " +
            "join teacher t on t.id = a.teacher_id " +
            "join rehab_course c on c.id = a.course_id " +
            "join teacher_schedule s on s.id = a.schedule_id " +
            "where a.teacher_id = #{teacherId} order by s.start_time desc")
    List<AppointmentDetailVO> listDetailsByTeacher(Long teacherId);

    @Select("select a.*, st.name as student_name, t.name as teacher_name, " +
            "c.name as course_name, c.category as course_category, " +
            "s.start_time, s.end_time " +
            "from appointment a " +
            "join student st on st.id = a.student_id " +
            "join teacher t on t.id = a.teacher_id " +
            "join rehab_course c on c.id = a.course_id " +
            "join teacher_schedule s on s.id = a.schedule_id " +
            "where a.student_id = #{studentId} order by s.start_time desc")
    List<AppointmentDetailVO> listDetailsByStudent(Long studentId);

    @Select("select count(*) from appointment where schedule_id = #{scheduleId}")
    int countBySchedule(Long scheduleId);

    @Select("select * from appointment where status = 1 and create_time < #{deadline}")
    List<Appointment> listTimeoutPending(LocalDateTime deadline);

    @Update("update appointment set status = #{status}, cancel_reason = #{cancelReason}, update_time = now() " +
            "where id = #{id} and status = #{expectedStatus}")
    // 返回影响行数：1 表示状态迁移成功，0 表示记录已被其他并发操作修改。
    int updateStatusIfCurrent(@org.apache.ibatis.annotations.Param("id") Long id,
                              @org.apache.ibatis.annotations.Param("status") Integer status,
                              @org.apache.ibatis.annotations.Param("cancelReason") String cancelReason,
                              @org.apache.ibatis.annotations.Param("expectedStatus") Integer expectedStatus);

    @Update("update appointment set status = 4, cancel_reason = #{reason}, update_time = now() " +
            "where id = #{id} and status in (1,2)")
    // 学生仅能取消待确认或已确认预约；条件写入比“先查再改”更能抵抗并发竞争。
    int cancelIfActive(@org.apache.ibatis.annotations.Param("id") Long id,
                       @org.apache.ibatis.annotations.Param("reason") String reason);
}
