package com.googlecode.mapperdao.utils

import com.googlecode.mapperdao.MapperDao
import com.googlecode.mapperdao.Entity
import com.googlecode.mapperdao.SelectConfig
import com.googlecode.mapperdao.UpdateConfig
import com.googlecode.mapperdao.DeleteConfig
import com.googlecode.mapperdao.DeclaredIds

/**
 * mixin to add CRUD methods to a dao
 *
 * https://code.google.com/p/mapperdao/wiki/CRUDDaos
 *
 * T is the entity type, i.e. Product
 * ID is the type of the key, i.e. Int or String
 *
 * @author kostantinos.kougios
 *
 *         30 Aug 2011
 */
trait CRUD[ID, T]
{
	protected val mapperDao: MapperDao
	protected val entity: Entity[ID, T]

	// override these to customise them
	protected val selectConfig = SelectConfig.default
	protected val updateConfig = UpdateConfig.default
	protected val deleteConfig = DeleteConfig.default

	/**
	 * insert an entity into the database
	 */
	def create(t: T): T with DeclaredIds[ID] = mapperDao.insert(updateConfig, entity, t)

	/**
	 * update an entity. The entity must have been retrieved from the database and then
	 * changed prior to calling this method.
	 * The whole tree will be updated (if necessary).
	 * The method heavily relies on object equality to assess which entities will be updated.
	 */
	def update(t: T with DeclaredIds[ID]): T with DeclaredIds[ID] = mapperDao.update(updateConfig, entity, t)

	def merge(t: T, id: ID): T with DeclaredIds[ID] = mapperDao.merge(selectConfig, updateConfig, entity, t, id)

	/**
	 * update an immutable entity. The entity must have been retrieved from the database. Because immutables can't change, a new instance
	 * of the entity must be created with the new values prior to calling this method. Values that didn't change should be copied from o.
	 * The method heavily relies on object equality to assess which entities will be updated.
	 * The whole tree will be updated (if necessary).
	 */
	def update(oldValue: T with DeclaredIds[ID], newValue: T): T with DeclaredIds[ID] = mapperDao.update(updateConfig, entity, oldValue, newValue)

	/**
	 * select an entity by it's primary key
	 *
	 * @return			Option[T] or None
	 */
	def retrieve(pk: ID): Option[T with DeclaredIds[ID]] = mapperDao.select(selectConfig, entity, pk)

	/**
	 * delete a persisted entity
	 */
	def delete(t: T with DeclaredIds[ID]): T = mapperDao.delete(deleteConfig, entity, t)

	/**
	 * this will delete an entity based on it's id.
	 *
	 * The delete will cascade to related entities only if there are cascade constraints
	 * on the foreign keys in the database.
	 */
	def delete(id: ID): Unit = mapperDao.delete(entity, id)

	/**
	 * unlinks an entity from mapperdao. The entity is not tracked for changes and can't
	 * be used in updates or deletes. This can free up some memory
	 */
	def unlink(o: T): T = mapperDao.unlink(entity, o)
}