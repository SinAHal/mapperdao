package com.googlecode.mapperdao.customization

import com.googlecode.mapperdao.state.persistcmds.{InsertCmd, PersistCmd}
import org.springframework.jdbc.core.SqlParameterValue
import com.googlecode.mapperdao.{SimpleColumn, Type}

/**
 * allows custom mapping from scala -> database and from database->scala types
 *
 * @author: kostas.kougios
 *          Date: 30/03/13
 */
abstract class UserDefinedDatabaseToScalaTypes extends CustomDatabaseToScalaTypes
{
	def transformValuesBeforeStoring(cmd: PersistCmd, sqlValues: List[SqlParameterValue]) = cmd match {
		case InsertCmd(tpe, _, columns, _) =>
			(columns zip sqlValues).map {
				case ((column, v), sqlValue) =>
					val (sqlType, newV) = scalaToDatabase(tpe, column, sqlValue.getSqlType, v)
					new SqlParameterValue(sqlType, newV)
			}
		case _ => sqlValues
	}


	def transformValuesAfterSelecting(tpe: Type[_, _], column: SimpleColumn, v: Any) = databaseToScala(tpe, column, v)

	def scalaToDatabase(tpe: Type[_, _], column: SimpleColumn, sqlType: Int, oldV: Any): (Int, Any)

	def databaseToScala(tpe: Type[_, _], column: SimpleColumn, v: Any): Any
}
