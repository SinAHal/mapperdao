package com.googlecode.mapperdao.plugins

import com.googlecode.mapperdao.MapperDao
import com.googlecode.mapperdao.utils.MapOfList
import com.googlecode.mapperdao.UpdateEntityMap
import com.googlecode.mapperdao.Type
import com.googlecode.mapperdao.utils.LowerCaseMutableMap
import com.googlecode.mapperdao.jdbc.JdbcMap
import com.googlecode.mapperdao.EntityMap
import com.googlecode.mapperdao.ManyToMany
import com.googlecode.mapperdao.SelectConfig
import com.googlecode.mapperdao.ValuesMap
import com.googlecode.mapperdao.utils.TraversableSeparation
import com.googlecode.mapperdao.Persisted
import com.googlecode.mapperdao.DeleteConfig
import com.googlecode.mapperdao.SimpleColumn
import com.googlecode.mapperdao.events.Events
import com.googlecode.mapperdao.TypeRegistry
import com.googlecode.mapperdao.drivers.Driver
import com.googlecode.mapperdao.MapperDaoImpl
import com.googlecode.mapperdao.UpdateConfig
import com.googlecode.mapperdao.Entity
import com.googlecode.mapperdao.ExternalEntity
import com.googlecode.mapperdao.TypeManager
import com.googlecode.mapperdao.ColumnInfoTraversableManyToMany
import com.googlecode.mapperdao.InsertExternalManyToMany
import com.googlecode.mapperdao.UpdateExternalManyToMany
import com.googlecode.mapperdao.SelectExternalManyToMany
import com.googlecode.mapperdao.DeleteExternalManyToMany

/**
 * @author kostantinos.kougios
 *
 * 31 Aug 2011
 */
class ManyToManyInsertPlugin(typeManager: TypeManager, typeRegistry: TypeRegistry, driver: Driver, mapperDao: MapperDaoImpl) extends PostInsert {
	override def after[PC, T](updateConfig: UpdateConfig, entity: Entity[PC, T], o: T, mockO: T with PC, entityMap: UpdateEntityMap, modified: LowerCaseMutableMap[Any], modifiedTraversables: MapOfList[String, Any]): Unit =
		{
			val table = entity.tpe.table
			// many to many
			table.manyToManyColumnInfos.foreach { cis =>
				val newKeyValues = table.primaryKeys.map(c => modified(c.columnName))
				val traversable = cis.columnToValue(o)
				val cName = cis.column.alias
				if (traversable != null) {
					val nestedEntity = cis.column.foreign.entity
					nestedEntity match {
						case ee: ExternalEntity[Any] =>
							val nestedTpe = ee.tpe
							val handler = ee.manyToManyOnInsertMap(cis.asInstanceOf[ColumnInfoTraversableManyToMany[_, _, Any]])
							traversable.foreach { nested =>
								val rightKeyValues = handler(InsertExternalManyToMany(updateConfig, o, nested))
								driver.doInsertManyToMany(nestedTpe, cis.column, newKeyValues, rightKeyValues.values)
								modifiedTraversables(cName) = nested
							}
						case ne: Entity[Any, Any] =>
							val nestedTpe = ne.tpe
							traversable.foreach { nested =>
								val newO = if (mapperDao.isPersisted(nested)) {
									nested
								} else {
									entityMap.down(mockO, cis, entity)
									val inserted = mapperDao.insertInner(updateConfig, ne, nested, entityMap)
									entityMap.up
									inserted
								}
								val rightKeyValues = nestedTpe.table.toListOfPrimaryKeyValues(newO)
								driver.doInsertManyToMany(nestedTpe, cis.column, newKeyValues, rightKeyValues)
								modifiedTraversables(cName) = newO
							}
					}
				}
			}
		}
}

/**
 * @author kostantinos.kougios
 *
 * 31 Aug 2011
 */
class ManyToManySelectPlugin(typeRegistry: TypeRegistry, driver: Driver, mapperDao: MapperDaoImpl) extends BeforeSelect with SelectMock {

	override def idContribution[PC, T](tpe: Type[PC, T], om: JdbcMap, entities: EntityMap, mods: scala.collection.mutable.HashMap[String, Any]): List[Any] = Nil

	override def before[PC, T](entity: Entity[PC, T], selectConfig: SelectConfig, om: JdbcMap, entities: EntityMap, mods: scala.collection.mutable.HashMap[String, Any]) =
		{
			val tpe = entity.tpe
			val table = tpe.table
			// many to many
			table.manyToManyColumnInfos.foreach { ci =>
				val c = ci.column
				val mtmR = if (selectConfig.skip(ci)) {
					Nil
				} else {
					val fe = c.foreign.entity
					val ftpe = fe.tpe.asInstanceOf[Type[Any, Any]]
					fe match {
						case ee: ExternalEntity[Any] =>
							val ids = tpe.table.primaryKeys.map { pk => om(pk.column.columnName) }
							val keys = c.linkTable.left zip ids
							val allIds = driver.doSelectManyToManyForExternalEntity(tpe, ftpe, c.asInstanceOf[ManyToMany[Any, Any]], keys)

							val handler = ee.manyToManyOnSelectMap(ci.asInstanceOf[ColumnInfoTraversableManyToMany[_, _, Any]])
							handler(SelectExternalManyToMany(selectConfig, allIds))
						case _ =>
							val ids = tpe.table.primaryKeys.map { pk => om(pk.column.columnName) }
							val keys = c.linkTable.left zip ids
							val fom = driver.doSelectManyToMany(tpe, ftpe, c.asInstanceOf[ManyToMany[Any, Any]], keys)
							entities.down(tpe, ci, om)
							val mtmR = mapperDao.toEntities(fom, fe, selectConfig, entities)
							entities.up
							mtmR
					}
				}
				mods(c.foreign.alias) = mtmR
			}
		}

	override def updateMock[PC, T](entity: Entity[PC, T], mods: scala.collection.mutable.HashMap[String, Any]) {
		mods ++= entity.tpe.table.manyToManyColumns.map(c => (c.alias -> List()))
	}
}

/**
 * @author kostantinos.kougios
 *
 * 31 Aug 2011
 */
class ManyToManyUpdatePlugin(typeRegistry: TypeRegistry, driver: Driver, mapperDao: MapperDaoImpl) extends PostUpdate {

	override def after[PC, T](updateConfig: UpdateConfig, entity: Entity[PC, T], o: T, mockO: T with PC, oldValuesMap: ValuesMap, newValuesMap: ValuesMap, entityMap: UpdateEntityMap, modified: MapOfList[String, Any]) =
		{
			val tpe = entity.tpe
			val table = tpe.table
			// update many-to-many
			table.manyToManyColumnInfos.filterNot(updateConfig.skip.contains(_)).foreach { ci =>
				val t = ci.columnToValue(o)
				val manyToMany = ci.column
				val newValues = t.toList
				val oldValues = oldValuesMap.seq[Any](manyToMany.foreign.alias)

				val pkLeft = oldValuesMap.toListOfColumnValue(table.primaryKeys)
				val pkArgs = manyToMany.linkTable.left zip pkLeft

				val (added, intersection, removed) = TraversableSeparation.separate(oldValues, newValues)

				val fe = manyToMany.foreign.entity.asInstanceOf[Entity[Any, Any]]
				val ftpe = fe.tpe

				manyToMany.foreign.entity match {
					case ee: ExternalEntity[Any] =>
						val handler = ee.manyToManyOnUpdateMap(ci.asInstanceOf[ColumnInfoTraversableManyToMany[_, _, Any]])
						// delete the removed ones
						removed.foreach { p =>
							val ftable = ftpe.table
							val rightKeyValues = handler(UpdateExternalManyToMany(updateConfig, UpdateExternalManyToMany.Operation.Remove, o, p))

							val fPkArgs = manyToMany.linkTable.right zip rightKeyValues.values
							driver.doDeleteManyToManyRef(tpe, ftpe, manyToMany, pkArgs, fPkArgs)
						}
						// update those that remained in the updated traversable
						intersection.foreach { p =>
							modified(manyToMany.alias) = p
						}
						// update the added ones
						added.foreach { p =>
							val fPKArgs = handler(UpdateExternalManyToMany(updateConfig, UpdateExternalManyToMany.Operation.Add, o, p))
							driver.doInsertManyToMany(tpe, manyToMany, pkLeft, fPKArgs.values)
							modified(manyToMany.alias) = p
						}

					case _ =>
						// delete the removed ones
						removed.foreach {
							case p: Persisted =>
								val ftable = ftpe.table
								val fPkArgs = manyToMany.linkTable.right zip ftable.toListOfPrimaryKeyValues(p)
								driver.doDeleteManyToManyRef(tpe, ftpe, manyToMany, pkArgs, fPkArgs)
								p.discarded = true
						}

						// update those that remained in the updated traversable
						intersection.foreach { item =>
							val newItem = item match {
								case p: Persisted =>
									entityMap.down(mockO, ci, entity)
									mapperDao.updateInner(updateConfig, fe, item, entityMap)
									entityMap.up
									p.discarded = true
									p
								case _ =>
									throw new IllegalStateException("Object not persisted but still exists in intersection of old and new collections. Please use the persisted entity when modifying the collection. The not persisted object is %s.".format(item))
							}
							modified(manyToMany.alias) = newItem
						}

						// update the added ones
						added.foreach { item =>
							val newItem = item match {
								case p: Persisted => p
								case n =>
									entityMap.down(mockO, ci, entity)
									val inserted = mapperDao.insertInner[Any, Any](updateConfig, fe, n, entityMap)
									entityMap.up
									inserted
							}
							val fPKArgs = ftpe.table.toListOfPrimaryKeyValues(newItem)
							driver.doInsertManyToMany(tpe, manyToMany, pkLeft, fPKArgs)
							modified(manyToMany.alias) = newItem
						}
				}
			}
		}
}

class ManyToManyDeletePlugin(driver: Driver, mapperDao: MapperDaoImpl) extends BeforeDelete {

	override def idColumnValueContribution[PC, T](tpe: Type[PC, T], deleteConfig: DeleteConfig, events: Events, o: T with PC with Persisted, entityMap: UpdateEntityMap): List[(SimpleColumn, Any)] = Nil

	override def before[PC, T](entity: Entity[PC, T], deleteConfig: DeleteConfig, events: Events, o: T with PC with Persisted, keyValues: List[(SimpleColumn, Any)], entityMap: UpdateEntityMap) = if (deleteConfig.propagate) {
		val tpe = entity.tpe
		tpe.table.manyToManyColumnInfos.filterNot(deleteConfig.skip(_)).foreach { ci =>
			// execute before-delete-relationship events
			events.executeBeforeDeleteRelationshipEvents(tpe, ci, o)

			driver.doDeleteAllManyToManyRef(tpe, ci.column, keyValues.map(_._2))

			ci.column.foreign.entity match {
				case ee: ExternalEntity[Any] =>
					val fo = ci.columnToValue(o)
					ee.manyToManyOnDeleteMap(ci.asInstanceOf[ColumnInfoTraversableManyToMany[_, _, Any]])(DeleteExternalManyToMany(deleteConfig, o, fo))
				case _ =>
			}

			// execute after-delete-relationship events
			events.executeAfterDeleteRelationshipEvents(tpe, ci, o)
		}
	}
}