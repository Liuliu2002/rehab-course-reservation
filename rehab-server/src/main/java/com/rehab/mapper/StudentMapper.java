package com.rehab.mapper;

import com.rehab.pojo.entity.Student;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface StudentMapper {
    @Select("select * from student where phone = #{phone}")
    Student getByPhone(String phone);

    @Select("select * from student where id = #{id}")
    Student getById(Long id);

    @Insert("insert into student(name,phone,password,status,create_time) values(#{name},#{phone},#{password},1,#{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Student student);

    @Update("update student set password = #{password} where id = #{id}")
    void updatePassword(@Param("id") Long id, @Param("password") String password);

    default Student createDefault(String phone, String password) {
        Student student = new Student();
        student.setName("学生" + phone.substring(Math.max(0, phone.length() - 4)));
        student.setPhone(phone);
        student.setPassword(password);
        student.setStatus(1);
        student.setCreateTime(LocalDateTime.now());
        insert(student);
        return student;
    }
}
