package com.joysim.common.handler;

import org.apache.ibatis.type.MappedTypes;

import com.joysim.common.enums.DeleteStateEnum;
import com.joysim.techdpt.model.oil.enums.OilOrderStateEnum;
import com.joysim.techdpt.model.oil.enums.OilProductStateEnum;

/**
 * 引进枚举类
 * @author ganx
 * @date 2020年3月31日 下午6:03:41
 * @param <E>
 */
@MappedTypes(value = { DeleteStateEnum.class, OilProductStateEnum.class, OilOrderStateEnum.class })
public class EnumTypeHandler<E extends Enum<E>> extends BaseEnumTypeHandler<E> {
	public EnumTypeHandler(Class<E> type) {
		super(type);
	}
}
