package com.googlecode.mapperdao.plugins

import com.googlecode.mapperdao._
import com.googlecode.mapperdao.schema.{Type, SimpleColumn, ColumnBase}
import com.googlecode.mapperdao.internal.{EntityMap, UpdateEntityMap}

/**
 * plugins executed before the main entity is created, during select operations
 *
 * @author kostantinos.kougios
 *
 *         31 Aug 2011
 */
trait BeforeSelect
{
	def idContribution[ID, T](
		tpe: Type[ID, T],
		om: DatabaseValues,
		entities: EntityMap
		): List[Any]

	def before[ID, T](
		entity: EntityBase[ID, T],
		selectConfig: SelectConfig,
		om: DatabaseValues,
		entities: EntityMap
		): List[SelectMod]
}

trait SelectMock
{
	def updateMock[ID, T](
		tpe: Type[ID, T],
		mods: scala.collection.mutable.Map[String, Any]
		)
}

/**
 * plugins executed before deleting an entity
 */
trait BeforeDelete
{
	def idColumnValueContribution[ID, T](
		tpe: Type[ID, T],
		deleteConfig: DeleteConfig,
		o: T with Persisted,
		entityMap: UpdateEntityMap
		): List[(SimpleColumn, Any)]

	def before[ID, T](
		entity: Entity[ID, Persisted, T],
		deleteConfig: DeleteConfig,
		o: T with Persisted,
		keyValues: List[(ColumnBase, Any)],
		entityMap: UpdateEntityMap
		)
}
