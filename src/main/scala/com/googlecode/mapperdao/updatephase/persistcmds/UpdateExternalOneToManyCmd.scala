package com.googlecode.mapperdao.updatephase.persistcmds

import com.googlecode.mapperdao._
import updatephase.prioritise.Priority
import com.googlecode.mapperdao.schema.ColumnInfoTraversableOneToMany

/**
 * signals an update which links an entity with an external entity
 *
 * @author: kostas.kougios
 *          Date: 28/12/12
 */
case class UpdateExternalOneToManyCmd[ID, T, FID, FT](
	foreignEntity: ExternalEntity[FID, FT],
	oneToMany: ColumnInfoTraversableOneToMany[ID, T, FID, FT],
	entityVM: ValuesMap,
	added: Traversable[FT],
	intersection: Traversable[(FT, FT)],
	removed: Traversable[FT]
	) extends PersistCmd
{
	def priority = Priority.Low
}