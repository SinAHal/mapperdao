package com.googlecode.mapperdao.state.persistcmds

import com.googlecode.mapperdao._
import state.prioritise.Priority

/**
 * @author kostantinos.kougios
 *         18 Dec 2012
 */
case class InsertManyToManyCmd[ID, T, FID, FT](
	tpe: Type[ID, T],
	foreignTpe: Type[FID, FT],
	manyToMany: ManyToMany[FID, FT],
	entityVM: ValuesMap,
	foreignEntityVM: ValuesMap
	) extends PersistCmd {
	def priority = Priority.Low
}