package com.rehab.mapper;

import com.rehab.pojo.entity.TeacherSchedule;
import com.rehab.pojo.vo.TeacherScheduleDetailVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ScheduleMapper {
    @Insert("insert into teacher_schedule(teacher_id,start_time,end_time,status,create_time) " +
            "values(#{teacherId},#{startTime},#{endTime},#{status},#{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(TeacherSchedule schedule);

    @Select("select count(*) from teacher_schedule where teacher_id = #{teacherId} and status in (1,2) " +
            "and start_time < #{endTime} and end_time > #{startTime}")
    // 排班冲突判断：两个时间段只要 start < otherEnd 且 end > otherStart 就代表有重叠。
    Integer countTimeConflict(@Param("teacherId") Long teacherId,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

    @Select("select count(*) from teacher_schedule where teacher_id = #{teacherId} and id <> #{id} " +
            "and status in (1,2) and start_time < #{endTime} and end_time > #{startTime}")
    Integer countTimeConflictExcluding(@Param("teacherId") Long teacherId,
                                       @Param("id") Long id,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);

    @Select("select * from teacher_schedule where id = #{id}")
    TeacherSchedule getById(Long id);

    @Select("select * from teacher_schedule where teacher_id = #{teacherId} and status = 1 and start_time > now() order by start_time")
    List<TeacherSchedule> listAvailableByTeacher(Long teacherId);

    @Select("select s.id as schedule_id, s.teacher_id, s.start_time, s.end_time, s.status as schedule_status, " +
            "a.id as appointment_id, a.appointment_no, a.status as appointment_status, a.student_id, a.course_id, " +
            "c.name as course_name, c.category as course_category " +
            "from teacher_schedule s " +
            "left join appointment a on a.id = (" +
            "select max(a2.id) from appointment a2 " +
            "where a2.schedule_id = s.id and a2.status in (1, 2, 3)) " +
            "left join rehab_course c on c.id = a.course_id " +
            "where s.teacher_id = #{teacherId} " +
            "order by (s.start_time < now()), " +
            "case when s.start_time >= now() then s.start_time end asc, s.start_time desc")
    List<TeacherScheduleDetailVO> listDetailsByTeacher(Long teacherId);

    @Update("update teacher_schedule set start_time = #{startTime}, end_time = #{endTime} " +
            "where id = #{id} and status in (1,3)")
    int updateTime(@Param("id") Long id,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime);

    @Update("update teacher_schedule set status = #{status} where id = #{id} and status = #{expectedStatus}")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("status") Integer status,
                              @Param("expectedStatus") Integer expectedStatus);

    @Delete("delete from teacher_schedule where id = #{id} and status = 3")
    int deleteDisabled(Long id);

    @Update("update teacher_schedule set status = 2 where id = #{id} and status = 1")
    // 条件更新是数据库层的第二道保险：只有可预约状态才能被占用。
    Integer occupyAvailable(Long id);

    @Update("update teacher_schedule set status = 1 where id = #{id} and status = 2")
    // 取消/拒绝/超时释放时，只把已占用的排班恢复为可预约。
    Integer releaseOccupied(Long id);
}
