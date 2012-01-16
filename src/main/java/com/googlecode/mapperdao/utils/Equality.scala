package com.googlecode.mapperdao.utils

import com.googlecode.mapperdao.ColumnBase
import com.googlecode.mapperdao.ValuesMap

/**
 * mapperdao has it's own way of considering equality of objects.
 * Here are the utility methods.
 *
 * @author kostantinos.kougios
 *
 * 7 Sep 2011
 */
protected[mapperdao] object Equality {

	def isEqual(o1: Any, o2: Any): Boolean = o1 match {
		case _: String => o1 == o2
		case _: Int | _: Boolean | _: Long | _: Byte | _: Short | _: Char => o1 == o2
		case _: Float => o1 == o2
		case _: Double => o1 == o2
		case a1: AnyRef => o2 match {
			case a2: AnyRef => a1.eq(a2)
			case null => false
		}
		case null => o2 == null
	}

	def onlyChanged(column: ColumnBase, newValuesMap: ValuesMap, oldValuesMap: ValuesMap): Boolean = !isEqual(newValuesMap.valueOf[AnyRef](column.alias), oldValuesMap.valueOf(column.alias))
}