package com.oceanli.vip;

import static org.junit.Assert.assertTrue;

import com.oceanli.vip.entity.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.StringUtils;

import javax.persistence.Column;
import javax.persistence.Table;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit test for simple App.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
public class AppTest 
{

    @Test
    public void test()
    {
        User user = new User();
        user.setName("oceanli");
        user.setAge(30);
        testJdbc(user);
    }

    public void testJdbc(Object condition) {

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<Object> result = new ArrayList<>();
        try {

            Class<?> conditionClass = condition.getClass();
            //查询实体类上定义的表名，默认为实体类的名称
            String tableName = conditionClass.getSimpleName();
            if (conditionClass.isAnnotationPresent(Table.class)) {
                Table tableAnnotation = conditionClass.getAnnotation(Table.class);
                tableName = tableAnnotation.name();
            }
            //表列的列表
            List<String> selectColumnNames = new ArrayList<>();
            //表列映射到实体类的属性
            Map<String, Field> columnToFieldMap = new HashMap<>();
            Field[] fields = conditionClass.getDeclaredFields();
            StringBuffer whereSql = new StringBuffer();
            for (Field field : fields) {

                field.setAccessible(true);
                String fieldName = field.getName();
                //默认设置列名为属性名
                columnToFieldMap.put(fieldName, field);
                String columnName = fieldName;
                //判断若属性名配置了Column注解，说明列名是自定义的
                if (field.isAnnotationPresent(Column.class)) {
                    Column column = field.getAnnotation(Column.class);
                    columnName = column.name();
                    columnToFieldMap.put(columnName, field);
                }
                //添加列名到查询的字段列表中
                selectColumnNames.add(columnName);

                //设置where条件
                whereSql = whereSql.append(setWhereSql(condition, field, columnName));
            }

            //1.加载驱动
            Class.forName("com.mysql.jdbc.Driver");
            //2.获取连接
            conn = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/jdbc-test?characterEncoding=UTF-8&rewriteBatchedStatements=true&useSSL=false",
                    "root", "123456");
            //3.获取语句集
            String sql = "select " + StringUtils.collectionToDelimitedString(selectColumnNames, ",") + " from " + tableName + " where 1=1 ";
            System.out.println(sql + whereSql.toString());
            ps = conn.prepareStatement(sql + whereSql.toString());
            //4.执行语句集
            rs = ps.executeQuery();
            //5.获取结果集
            buildResult(rs, conditionClass, result, columnToFieldMap);
            System.out.println(result);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //6.关闭结果集、语句集、连接
            try {
                if (null != rs) {
                    rs.close();
                }
                if (null != ps) {
                    ps.close();
                }
                if (null != conn) {
                    conn.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private String setWhereSql(Object condition, Field field, String columnName) throws IllegalAccessException {

        StringBuffer whereSql = new StringBuffer();
        Object fieldValue = field.get(condition);
        if (fieldValue != null) {
            whereSql.append("and ").append(columnName).append(" = ");
            if (field.getType() == String.class) {
                whereSql.append("'" + fieldValue + "'");
            } else {
                whereSql.append(fieldValue);
            }
        }
        return whereSql.toString();
    }

    private void buildResult(ResultSet rs, Class<?> entityClass, List<Object> result, Map<String, Field> columnToFieldMap) throws Exception {
        //获取表的列数量
        int columnCount = rs.getMetaData().getColumnCount();
        while (rs.next()) {
            Object instance = entityClass.newInstance();
            /*User user = new User();
            user.setId(rs.getInt("id"));
            user.setName(rs.getString("name"));
            user.setAge(rs.getInt("age"));
            user.setAddr(rs.getString("addr"));
            result.add(user);*/
            //获取表的每列的列名，再通过列表找到对应实体的属性，通过反射将属性设置宁城
            for(int i = 1; i <= columnCount; i++) {
                String columnName = rs.getMetaData().getColumnName(i);
                Field field = columnToFieldMap.get(columnName);
                field.setAccessible(true);
                field.set(instance, rs.getObject(columnName));
            }
            result.add(instance);
        }
    }
}
