package com.googlecode.mapperdao.state.prioritise

import com.googlecode.mapperdao.Entity
import com.googlecode.mapperdao.ColumnInfoTraversableManyToMany
import com.googlecode.mapperdao.utils.IdentityMap
import com.googlecode.mapperdao.ColumnInfoManyToOne
import com.googlecode.mapperdao.ColumnInfoTraversableOneToMany

/**
 * @author kostantinos.kougios
 *
 * 15 Dec 2012
 */
class PriorityPhase {
	private var visited = Set[Entity[_, _, _]]()

	def prioritise(entity: Entity[_, _, _]): List[Entity[_, _, _]] =
		if (visited(entity))
			Nil
		else {
			visited += entity

			val after = entity.tpe.table.relationshipColumnInfos.collect {
				case ColumnInfoTraversableManyToMany(column, _, _) =>
					prioritise(column.foreign.entity)
				case ColumnInfoTraversableOneToMany(column, _, _, _) =>
					prioritise(column.foreign.entity)
			}.flatten

			val before = entity.tpe.table.relationshipColumnInfos.collect {
				case ColumnInfoManyToOne(column, _, _) =>
					prioritise(column.foreign.entity)
			}.flatten

			(before ::: entity :: after).distinct
		}
}