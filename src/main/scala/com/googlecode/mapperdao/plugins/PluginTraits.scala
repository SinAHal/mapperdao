package com.googlecode.mapperdao.plugins

import com.googlecode.mapperdao._

/**
 * plugins executed before the main entity is created, during select operations
 *
 * @author kostantinos.kougios
 *
 *         31 Aug 2011
 */
trait BeforeSelect {
	def idContribution[ID, T](
		tpe: Type[ID, T],
		om: DatabaseValues,
		entities: EntityMap
	): List[Any]

	def before[ID, T](
		entity: Entity[ID, T],
		selectConfig: SelectConfig,
		om: DatabaseValues,
		entities: EntityMap
	): List[SelectMod]
}

trait SelectMock {
	def updateMock[ID, T](
		entity: Entity[ID, T],
		mods: scala.collection.mutable.Map[String, Any]
	)
}

/**
 * plugins executed before deleting an entity
 */
trait BeforeDelete {
	def idColumnValueContribution[ID, T](
		tpe: Type[ID, T],
		deleteConfig: DeleteConfig,
		o: T with DeclaredIds[ID],
		entityMap: UpdateEntityMap
	): List[(SimpleColumn, Any)]

	def before[ID, T](
		entity: Entity[ID, T],
		deleteConfig: DeleteConfig,
		o: T with DeclaredIds[ID],
		keyValues: List[(ColumnBase, Any)],
		entityMap: UpdateEntityMap
	): Unit
}
