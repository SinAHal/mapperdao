package com.googlecode.mapperdao.state.prioritise

import com.googlecode.mapperdao.Entity
import com.googlecode.mapperdao.ColumnInfoTraversableManyToMany
import com.googlecode.mapperdao.ColumnInfoManyToOne
import com.googlecode.mapperdao.ColumnInfoTraversableOneToMany
import com.googlecode.mapperdao.state.persistcmds.{DeleteManyToManyCmd, PersistCmd, CmdWithEntity, InsertManyToManyCmd}
import com.googlecode.mapperdao.DeclaredIds

/**
 * @author kostantinos.kougios
 *
 *         15 Dec 2012
 */
class PriorityPhase {
	private var visited = Set[Entity[_, _, _]]()

	def prioritise[ID, PC <: DeclaredIds[ID], T](
		entity: Entity[ID, PC, T],
		cmds: List[PersistCmd]
	): List[List[PersistCmd]] = {
		val prie = prioritiseEntities(entity)

		val (high, low) = cmds.partition {
			case _: InsertManyToManyCmd[_, _, _, _] => false
			case _: DeleteManyToManyCmd[_, _, _, _] => false
			case _ => true
		}
		val groupped = high.collect {
			case we: CmdWithEntity[_, _] => we
		}.groupBy(_.entity.asInstanceOf[Entity[_, _, _]])

		val h = prie.filter(groupped.contains(_)).map {
			e =>
				groupped(e)
		}
		h ::: List(low)
	}

	def prioritiseEntities(entity: Entity[_, _, _]): List[Entity[_, _, _]] =
		if (visited(entity))
			Nil
		else {
			visited += entity

			val after = entity.tpe.table.relationshipColumnInfos.collect {
				case ColumnInfoTraversableManyToMany(column, _, _) =>
					prioritiseEntities(column.foreign.entity)
				case ColumnInfoTraversableOneToMany(column, _, _, _) =>
					prioritiseEntities(column.foreign.entity)
			}.flatten

			val before = entity.tpe.table.relationshipColumnInfos.collect {
				case ColumnInfoManyToOne(column, _, _) =>
					prioritiseEntities(column.foreign.entity)
			}.flatten

			(before ::: entity :: after).distinct
		}
}