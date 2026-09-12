package com.rehab.mapper;

import com.rehab.pojo.entity.RehabCourse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CourseMapper {
    @Insert("insert into rehab_course(name,category,suitable_crowd,training_goal,duration_minutes,price,status,create_time,update_time) " +
            "values(#{name},#{category},#{suitableCrowd},#{trainingGoal},#{durationMinutes},#{price},#{status},#{createTime},#{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(RehabCourse course);

    @Select("select * from rehab_course where status = 1 order by id desc")
    List<RehabCourse> listEnabled();

    @Select("select * from rehab_course order by id desc")
    List<RehabCourse> listAll();

    @Select("select * from rehab_course where id = #{id}")
    RehabCourse getById(Long id);

    @Update("update rehab_course set status = #{status}, update_time = now() where id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
