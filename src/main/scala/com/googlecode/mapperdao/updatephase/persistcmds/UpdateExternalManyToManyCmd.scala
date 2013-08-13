package com.googlecode.mapperdao.updatephase.persistcmds

import com.googlecode.mapperdao._
import updatephase.prioritise.Priority
import com.googlecode.mapperdao.schema.{Type, ColumnInfoTraversableManyToMany}

/**
 * signals an update which links an entity with an external entity
 *
 * @author: kostas.kougios
 *          Date: 28/12/12
 */
case class UpdateExternalManyToManyCmd[ID, T, FID, FT](
	tpe: Type[ID, T],
	newVM: ValuesMap,
	foreignEntity: ExternalEntity[FID, FT],
	manyToMany: ColumnInfoTraversableManyToMany[T, FID, FT],
	added: Traversable[FT],
	intersection: Traversable[(FT, FT)],
	removed: Traversable[FT]
	) extends PersistCmd
{
	def priority = Priority.Low
}