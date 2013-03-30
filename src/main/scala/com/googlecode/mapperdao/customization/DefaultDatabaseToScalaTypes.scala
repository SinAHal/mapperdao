package com.googlecode.mapperdao.customization

import com.googlecode.mapperdao.state.persistcmds.PersistCmd
import com.googlecode.mapperdao.{SimpleColumn, Type}

/**
 * @author: kostas.kougios
 *          Date: 30/03/13
 */
object DefaultDatabaseToScalaTypes extends CustomDatabaseToScalaTypes
{
	def transformValuesBeforeStoring(cmd: PersistCmd, sqlValues: List[(SimpleColumn, Any)]) = sqlValues

	def transformValuesAfterSelecting(tpe: Type[_, _], column: SimpleColumn, v: Any) = v
}
