package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.JdbcMap
import org.joda.time.DateTime

/**
 * manages types
 *
 * Jdbc drivers return different types for different columns, i.e. number(20,0) might return a BigDecimal in some implementations
 * and a Long in others. This manager takes care of conversions.
 *
 * @author kostantinos.kougios
 *
 * 30 Jul 2011
 */
trait TypeManager {

	def normalize(v: Any): Any
	/**
	 * converts o to tpe if possible
	 */
	def toActualType(tpe: Class[_], o: Any): Any

	def correctTypes[ID, PC <: DeclaredIds[ID], T](table: Table[ID, PC, T], j: JdbcMap): DatabaseValues
}