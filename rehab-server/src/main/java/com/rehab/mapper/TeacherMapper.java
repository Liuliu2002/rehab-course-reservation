package com.rehab.mapper;

import com.rehab.pojo.entity.Teacher;
import com.rehab.pojo.vo.TeacherAdminVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TeacherMapper {
    @Select("select * from teacher where phone = #{phone}")
    Teacher getByPhone(String phone);

    @Select("select * from teacher where id = #{id}")
    Teacher getById(Long id);

    @Select("select * from teacher where status = 1 order by id desc")
    List<Teacher> listEnabled();

    @Select("select id,name,phone,specialty,introduction,status,role,create_time " +
            "from teacher order by status desc, id desc")
    List<TeacherAdminVO> listForAdmin();

    @Insert("insert into teacher(name,phone,password,specialty,introduction,status,role,create_time,update_time) " +
            "values(#{name},#{phone},#{password},#{specialty},#{introduction},#{status},#{role},#{createTime},#{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Teacher teacher);

    @Select("select id from teacher where id = #{id} and status = 1 for update")
    Long lockActiveById(Long id);

    @Update("update teacher set password = #{password}, update_time = now() where id = #{id}")
    void updatePassword(@Param("id") Long id, @Param("password") String password);

    @Update("update teacher set status = #{status}, update_time = now() where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
