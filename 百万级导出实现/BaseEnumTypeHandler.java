package com.joysim.common.handler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.lang.StringUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * 自定义枚举类的转换器
 * @author ganx
 * @date 2020年3月31日 下午5:50:42
 * @param <E>
 */
public class BaseEnumTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {

	private Class<E> type;
	
    public BaseEnumTypeHandler() {}

	public BaseEnumTypeHandler(Class<E> type) {
		this.type = type;
	}
	
	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, E parameter,
			JdbcType jdbcType) throws SQLException {
		if (jdbcType == null) {
			ps.setString(i, parameter.toString());
		} else {
			ps.setObject(i, parameter.name(), jdbcType.TYPE_CODE);
		}

	}

	@Override
	public E getNullableResult(ResultSet rs, String columnName)
			throws SQLException {
		return get(rs.getString(columnName));
	}

	@Override
	public E getNullableResult(ResultSet rs, int columnIndex)
			throws SQLException {
		return get(rs.getString(columnIndex));
	}

	@Override
	public E getNullableResult(CallableStatement cs, int columnIndex)
			throws SQLException {
		return get(cs.getString(columnIndex));
	}
	
	private <E extends Enum<E>> E get(String v) {
        if (v == null) {
        	return null;
        }
        if (StringUtils.isNumeric(v)) {
            return (E) get(type, Integer.parseInt(v));
        } else {
            return (E) Enum.valueOf(type, v);
        }
	}

	private <E extends Enum<E>> E get(Class<E> type, int v) {
        Method method = null;
        E result = null;
        try {
        	//约定用getItem方法
            method = type.getMethod("getItem", int.class);
            result = (E)method.invoke(type, v);
        } catch (NoSuchMethodException e) {
            result = Enum.valueOf(type, String.valueOf(v));
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return result;
    }
}
