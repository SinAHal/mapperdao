package com.googlecode.mapperdao.state.persistcmds

import com.googlecode.mapperdao._
import state.prioritise.Priority

/**
 * @author: kostas.kougios
 *          Date: 27/12/12
 */
case class InsertManyToManyExternalCmd[ID, T, FID, FT](
	tpe: Type[ID, T],
	foreignEntity: ExternalEntity[FID, FT],
	manyToMany: ColumnInfoTraversableManyToMany[T, FID, FT],
	entityVM: ValuesMap,
	foreignO: FT

	) extends PersistCmd {
	def priority = Priority.Low
}

