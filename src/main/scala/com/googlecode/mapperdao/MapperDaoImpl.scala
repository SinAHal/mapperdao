package com.googlecode.mapperdao

import com.googlecode.mapperdao.drivers.Driver
import scala.collection.mutable.HashMap
import com.googlecode.mapperdao.exceptions._
import com.googlecode.mapperdao.utils.MapOfList
import com.googlecode.mapperdao.plugins._
import com.googlecode.mapperdao.jdbc.JdbcMap
import com.googlecode.mapperdao.plugins.SelectMock
import com.googlecode.mapperdao.events.Events
import com.googlecode.mapperdao.utils.Equality
import com.googlecode.mapperdao.utils.NYI
import com.googlecode.mapperdao.utils.Helpers
import com.googlecode.mapperdao.jdbc.CmdToDatabase
import com.googlecode.mapperdao.state.persistcmds.PersistCmdFactory
import com.googlecode.mapperdao.state.persisted.PersistedNode

/**
 * @author kostantinos.kougios
 *
 * 13 Jul 2011
 */
protected final class MapperDaoImpl(
		val driver: Driver,
		events: Events,
		val typeManager: TypeManager) extends MapperDao {
	private val typeRegistry = driver.typeRegistry
	private val lazyLoadManager = new LazyLoadManager

	private val beforeInsertPlugins = List[BeforeInsert](
		new ManyToOneInsertPlugin(typeRegistry, this),
		new OneToManyInsertPlugin(typeRegistry, driver, this),
		new OneToOneReverseInsertPlugin(typeRegistry, this),
		new OneToOneInsertPlugin(typeRegistry, this)
	)

	private val postInsertPlugins = List[PostInsert](
		new OneToOneReverseInsertPlugin(typeRegistry, this),
		new OneToManyInsertPlugin(typeRegistry, driver, this),
		new ManyToManyInsertPlugin(typeManager, typeRegistry, driver, this)
	)

	private val selectBeforePlugins: List[BeforeSelect] = List(
		new ManyToOneSelectPlugin(typeRegistry, this),
		new OneToManySelectPlugin(typeRegistry, driver, this),
		new OneToOneReverseSelectPlugin(typeRegistry, driver, this),
		new OneToOneSelectPlugin(typeRegistry, driver, this),
		new ManyToManySelectPlugin(typeRegistry, driver, this)
	)

	private val mockPlugins: List[SelectMock] = List(
		new OneToManySelectPlugin(typeRegistry, driver, this),
		new ManyToManySelectPlugin(typeRegistry, driver, this),
		new ManyToOneSelectPlugin(typeRegistry, this),
		new OneToOneSelectPlugin(typeRegistry, driver, this)
	)

	private val beforeDeletePlugins: List[BeforeDelete] = List(
		new ManyToManyDeletePlugin(driver, this),
		new OneToManyDeletePlugin(typeRegistry, this),
		new OneToOneReverseDeletePlugin(typeRegistry, driver, this),
		new ManyToOneDeletePlugin
	)
	/**
	 * ===================================================================================
	 * Utility methods
	 * ===================================================================================
	 */

	private[mapperdao] def isPersisted(o: Any): Boolean = o.isInstanceOf[Persisted]

	/**
	 * ===================================================================================
	 * CRUD OPERATIONS
	 * ===================================================================================
	 */

	private[mapperdao] def insertInner[ID, T](
		updateConfig: UpdateConfig,
		node: PersistedNode[ID, T],
		entityMap: UpdateEntityMap): T with DeclaredIds[ID] with Persisted =
		// if a mock exists in the entity map or already persisted, then return
		// the existing mock/persisted object
		entityMap.get[DeclaredIds[ID], T](node.o).getOrElse {

			val o = node.o
			if (isPersisted(o)) throw new IllegalArgumentException("can't insert an object that is already persisted: " + o);
			val entity = node.entity
			val tpe = entity.tpe
			val table = tpe.table

			val modified = ValuesMap.fromEntity(typeManager, tpe, o).toMutableMap
			val modifiedTraversables = new MapOfList[String, Any](MapOfList.stringToLowerCaseModifier)

			val updateInfo @ UpdateInfo(parent, parentColumnInfo, parentEntity) = entityMap.peek[Any, DeclaredIds[Any], Any, Any, ID, DeclaredIds[ID], T]

			// create a mock
			var mockO = createMock(updateConfig.data, entity, modified ++ modifiedTraversables)
			entityMap.put(o, mockO)

			val extraArgs = beforeInsertPlugins.map { plugin =>
				plugin.before(updateConfig, node, mockO, entityMap, modified, updateInfo)
			}.flatten.distinct

			// arguments
			val args = table.toListOfColumnAndValueTuples(table.simpleTypeNotAutoGeneratedColumns, o) ::: extraArgs

			// insert entity
			if (!args.isEmpty || !table.simpleTypeAutoGeneratedColumns.isEmpty) {
				events.executeBeforeInsertEvents(tpe, args)
				val ur = node.keys.toMap
				events.executeAfterInsertEvents(tpe, args)

				table.simpleTypeAutoGeneratedColumns.foreach { c =>
					val ag = ur(c)
					// many drivers return the wrong type for the autogenerated
					// keys, typically instead of Int they return Long
					table.pcColumnToColumnInfoMap(c) match {
						case ci: ColumnInfo[_, _] =>
							val fixed = typeManager.toActualType(ci.dataType, ag)
							modified(c.name) = fixed
					}
				}
			}

			// create a more up-to-date mock
			mockO = createMock(updateConfig.data, entity, modified ++ modifiedTraversables)
			entityMap.put(o, mockO)

			postInsertPlugins.foreach { plugin =>
				plugin.after(updateConfig, node, mockO, entityMap, modified, modifiedTraversables)
			}

			val finalMods = modified ++ modifiedTraversables
			val newE = tpe.constructor(updateConfig.data, ValuesMap.fromMap(finalMods))
			// re-put the actual
			entityMap.put(o, newE)
			newE
		}

	override def insert[ID, PC <: DeclaredIds[ID], T](
		updateConfig: UpdateConfig,
		entity: Entity[ID, PC, T],
		os: List[T]): List[T with PC] =
		{
			val po = new PersistCmdFactory
			val cmds = os.map { o =>
				if (isPersisted(o)) throw new IllegalArgumentException("can't insert an object that is already persisted: " + o)
				po.toCmd(entity, o)
			}
			val ctd = new CmdToDatabase(updateConfig, driver, typeManager)
			val nodes = ctd.execute[ID, PC, T](cmds)
			val entityMap = new UpdateEntityMap
			nodes.map { node =>
				insertInner(updateConfig, node, entityMap)
			}.asInstanceOf[List[T with PC]]
		}

	/**
	 * update an entity
	 */
	private def updateInner[ID, PC <: DeclaredIds[ID], T](updateConfig: UpdateConfig, entity: Entity[ID, PC, T], o: T, oldValuesMap: ValuesMap, newValuesMap: ValuesMap, entityMap: UpdateEntityMap): T with PC with Persisted =
		{
			if (oldValuesMap == null)
				throw new IllegalStateException("old product in inconsistent state. Did you unlink it? For entity %s , value %s".format(entity, o))
			val tpe = entity.tpe
			def changed(column: ColumnBase) = !Equality.isEqual(newValuesMap.valueOf(column), oldValuesMap.valueOf(column))
			val table = tpe.table
			val modified = oldValuesMap.toMutableMap ++ newValuesMap.toMutableMap
			val modifiedTraversables = new MapOfList[String, Any](MapOfList.stringToLowerCaseModifier)

			// store a mock in the entity map so that we don't process the same instance twice
			var mockO = createMock(updateConfig.data, entity, modified ++ modifiedTraversables)
			entityMap.put(o, mockO)

			// first, lets update the simple columns that changed

			// run all DuringUpdate plugins
			// find out which simple columns changed
			val columnsChanged = table.simpleTypeNotAutoGeneratedColumns.filter(changed _)

			// if there is a change, update it
			val args = newValuesMap.toListOfSimpleColumnAndValueTuple(columnsChanged)
			if (!args.isEmpty) {
				val pkArgs = oldValuesMap.toListOfSimpleColumnAndValueTuple(table.primaryKeys)

				// we now need to take into account declarePrimaryKeys
				val unused = if (table.unusedPKs.isEmpty)
					Nil
				else {
					val alreadyUsed = pkArgs.map(_._1)
					val uc = table.unusedPKs.filterNot(alreadyUsed.contains(_))
					oldValuesMap.toListOfSimpleColumnAndValueTuple(uc)
				}

				val allKeys = pkArgs ::: unused
				// execute the before update events
				events.executeBeforeUpdateEvents(tpe, args, allKeys)

				driver.doUpdate(tpe, args, allKeys)

				// execute the after update events
				events.executeAfterUpdateEvents(tpe, args, allKeys)
			}

			// update the mock
			mockO = createMock(updateConfig.data, entity, modified ++ modifiedTraversables)
			entityMap.put(o, mockO)

			if (updateConfig.depth > 0) {
				val newUC = updateConfig.copy(depth = updateConfig.depth - 1)
			}

			// done, construct the updated entity
			val finalValuesMap = ValuesMap.fromMap(modified ++ modifiedTraversables)
			val v = tpe.constructor(updateConfig.data, finalValuesMap)
			entityMap.put(o, v)
			v
		}

	/**
	 * update an entity. The entity must have been retrieved from the database and then
	 * changed prior to calling this method.
	 * The whole object graph will be updated (if necessary).
	 */
	override def update[ID, PC <: DeclaredIds[ID], T](updateConfig: UpdateConfig, entity: Entity[ID, PC, T], o: T with PC): T with PC =
		{
			validatePersisted(o)
			val entityMap = new UpdateEntityMap
			try {
				val v = updateInner(updateConfig, entity, o, entityMap)
				entityMap.done
				v
			} catch {
				case e: Throwable => throw new PersistException("An error occured during update of entity %s with value %s.".format(entity, o), e)
			}
		}

	private[mapperdao] def updateInner[ID, PC <: DeclaredIds[ID], T](updateConfig: UpdateConfig, entity: Entity[ID, PC, T], o: T with PC, entityMap: UpdateEntityMap): T with PC with Persisted =
		// do a check if a mock is been updated
		o match {
			case p: Persisted if (p.mapperDaoMock) =>
				val v = o.asInstanceOf[T with PC with Persisted]
				// report an error if mock was changed by the user
				val tpe = entity.tpe
				val newVM = ValuesMap.fromEntity(typeManager, tpe, o, false)
				val oldVM = v.mapperDaoValuesMap
				if (newVM.isSimpleColumnsChanged(tpe, oldVM)) throw new IllegalStateException("please don't modify mock objects. Object %s is mock and has been modified.".format(p))
				v
			case _ =>
				// if a mock exists in the entity map or already persisted, then return
				// the existing mock/persisted object
				entityMap.get[PC, T](o).getOrElse {
					val persisted = o.asInstanceOf[T with PC with Persisted]
					val oldValuesMap = persisted.mapperDaoValuesMap
					val tpe = entity.tpe
					val newValuesMapPre = ValuesMap.fromEntity(typeManager, tpe, o)
					val reConstructed = tpe.constructor(updateConfig.data, newValuesMapPre)
					updateInner(updateConfig, entity, o, oldValuesMap, reConstructed.mapperDaoValuesMap, entityMap)
				}
		}
	/**
	 * update an immutable entity. The entity must have been retrieved from the database. Because immutables can't change, a new instance
	 * of the entity must be created with the new values prior to calling this method. Values that didn't change should be copied from o.
	 * For traversables, the method heavily relies on object equality to assess which entities will be updated. So please copy over
	 * traversable entities from the old collections to the new ones (but you can instantiate a new collection).
	 *
	 * The whole tree will be updated (if necessary).
	 *
	 * @param	o		the entity, as retrieved from the database
	 * @param	newO	the new instance of the entity with modifications. The database will be updated
	 * 					based on differences between newO and o
	 * @return			The updated entity. Both o and newO should be disposed (not used) after the call.
	 */
	override def update[ID, PC <: DeclaredIds[ID], T](updateConfig: UpdateConfig, entity: Entity[ID, PC, T], o: T with PC, newO: T): T with PC = {
		validatePersisted(o)
		o.mapperDaoDiscarded = true
		try {
			val entityMap = new UpdateEntityMap
			val v = updateInner(updateConfig, entity, o, newO, entityMap)
			entityMap.done
			v
		} catch {
			case e => throw new PersistException("An error occured during update of entity %s with old value %s and new value %s".format(entity, o, newO), e)
		}
	}

	private[mapperdao] def updateInner[ID, PC <: DeclaredIds[ID], T](updateConfig: UpdateConfig, entity: Entity[ID, PC, T], o: T with PC with Persisted, newO: T, entityMap: UpdateEntityMap): T with PC =
		{
			val oldValuesMap = o.mapperDaoValuesMap
			val newValuesMap = ValuesMap.fromEntity(typeManager, entity.tpe, newO)
			updateInner(updateConfig, entity, newO, oldValuesMap, newValuesMap, entityMap)
		}

	private def validatePersisted(persisted: Persisted) {
		if (persisted.mapperDaoDiscarded) throw new IllegalArgumentException("can't operate on an object twice. An object that was updated/deleted must be discarded and replaced by the return value of update(), i.e. onew=update(o) or just be disposed if it was deleted. The offending object was : " + persisted);
		if (persisted.mapperDaoMock) throw new IllegalArgumentException("can't operate on a 'mock' object. Mock objects are created when there are cyclic dependencies of entities, i.e. entity A depends on B and B on A on a many-to-many relationship.  The offending object was : " + persisted);
	}

	/**
	 * select an entity but load only part of the entity's graph. SelectConfig contains configuration regarding which relationships
	 * won't be loaded, i.e.
	 *
	 * SelectConfig(skip=Set(ProductEntity.attributes)) // attributes won't be loaded
	 */
	override def select[ID, PC <: DeclaredIds[ID], T](selectConfig: SelectConfig, entity: Entity[ID, PC, T], id: ID) =
		{
			if (id == null) throw new NullPointerException("ids can't be null")
			val ids = Helpers.idToList(id)
			val pkSz = entity.tpe.table.primaryKeysSize
			if (pkSz != ids.size) throw new IllegalArgumentException("entity has %d keys, can't use these keys: %s".format(pkSz, ids))
			val entityMap = new EntityMap
			val v = selectInner(entity, selectConfig, ids, entityMap)
			v
		}

	private[mapperdao] def selectInner[ID, PC <: DeclaredIds[ID], T](
		entity: Entity[ID, PC, T],
		selectConfig: SelectConfig,
		ids: List[Any],
		entities: EntityMap): Option[T with PC] =
		{
			val clz = entity.clz
			val tpe = entity.tpe
			if (tpe.table.primaryKeysSize != ids.size) throw new IllegalStateException("Primary keys number dont match the number of parameters. Primary keys: %s".format(tpe.table.primaryKeys))

			entities.get[T with PC](tpe.clz, ids) {
				try {
					val (pks, declared) = ids.splitAt(tpe.table.primaryKeys.size)
					val pkArgs = tpe.table.primaryKeys.zip(pks)
					// convert unused keys to their simple values
					val declaredArgs = if (tpe.table.unusedPKs.isEmpty)
						Nil
					else
						(
							(tpe.table.unusedPKColumnInfos zip declared) map {
								case (ci, v) =>
									ci match {
										case ci: ColumnInfoManyToOne[T, Any, DeclaredIds[Any], Any] =>
											val foreign = ci.column.foreign
											val fentity = foreign.entity
											val ftable = fentity.tpe.table
											ci.column.columns zip ftable.toListOfPrimaryKeyValues(v)
										case ci: ColumnInfoTraversableOneToMany[Any, DeclaredIds[Any], Any, Any, DeclaredIds[Any], Any] =>
											val fentity = ci.entityOfT
											val ftable = fentity.tpe.table
											ci.column.columns zip ftable.toListOfPrimaryKeyValues(v)
										case ci: ColumnInfoOneToOne[T, Any, DeclaredIds[Any], Any] =>
											val foreign = ci.column.foreign
											val fentity = foreign.entity
											val ftable = fentity.tpe.table
											ci.column.columns zip ftable.toListOfPrimaryKeyValues(v)
										case _ => throw new IllegalArgumentException("Please use declarePrimaryKey only for relationships. For normal data please use key(). This occured for entity %s".format(entity.getClass))
									}
							}).flatten

					val args = pkArgs ::: declaredArgs

					events.executeBeforeSelectEvents(tpe, args)
					val om = driver.doSelect(selectConfig, tpe, args)
					events.executeAfterSelectEvents(tpe, args)
					if (om.isEmpty) None
					else if (om.size > 1) throw new IllegalStateException("expected 1 result for %s and ids %s, but got %d. Is the primary key column a primary key in the table?".format(clz.getSimpleName, ids, om.size))
					else {
						val l = toEntities(om, entity, selectConfig, entities)
						Some(l.head)
					}
				} catch {
					case e => throw new QueryException("An error occured during select of entity %s and primary keys %s".format(entity, ids), e)
				}
			}
		}

	private[mapperdao] def toEntities[ID, PC <: DeclaredIds[ID], T](
		lm: List[DatabaseValues],
		entity: Entity[ID, PC, T],
		selectConfig: SelectConfig,
		entities: EntityMap): List[T with PC] =
		lm.map { jdbcMap =>
			val tpe = entity.tpe
			val table = tpe.table
			// calculate the id's for this tpe
			val pkIds = table.primaryKeys.map { pk => jdbcMap(pk) } ::: selectBeforePlugins.map {
				_.idContribution(tpe, jdbcMap, entities)
			}.flatten
			val unusedIds = table.unusedPKs.map { pk =>
				jdbcMap(pk)
			}
			val ids = pkIds ::: unusedIds
			if (ids.isEmpty)
				throw new IllegalStateException("entity %s without primary key, please use declarePrimaryKeys() to declare the primary key columns of tables into your entity declaration")

			entities.get[T with PC](tpe.clz, ids) {
				val mods = jdbcMap.toMap
				val mock = createMock(selectConfig.data, entity, mods)
				entities.putMock(tpe.clz, ids, mock)

				val allMods = mods ++ selectBeforePlugins.map {
					_.before(entity, selectConfig, jdbcMap, entities)
				}.flatten.map {
					case SelectMod(k, v, lazyBeforeLoadVal) =>
						(k, v)
				}.toMap

				val vm = ValuesMap.fromMap(allMods)
				// if the entity should be lazy loaded and it has relationships, then
				// we need to lazy load it
				val entityV = if (lazyLoadManager.isLazyLoaded(selectConfig.lazyLoad, entity)) {
					lazyLoadEntity(entity, selectConfig, vm)
				} else tpe.constructor(selectConfig.data, vm)
				Some(entityV)
			}.get
		}

	private def lazyLoadEntity[ID, PC <: DeclaredIds[ID], T](
		entity: Entity[ID, PC, T],
		selectConfig: SelectConfig,
		vm: ValuesMap) = {
		// substitute lazy loaded columns with empty values
		val tpe = entity.tpe
		val table = tpe.table
		val lazyLoad = selectConfig.lazyLoad

		val lazyLoadedMods = (table.columnInfosPlain.map { ci =>
			val ll = lazyLoad.isLazyLoaded(ci)
			ci match {
				case mtm: ColumnInfoTraversableManyToMany[_, _, _, _] =>
					(ci.column.alias, if (ll) Nil else vm.valueOf(ci))
				case mto: ColumnInfoManyToOne[_, _, _, _] =>
					(ci.column.alias, if (ll) null else vm.valueOf(ci))
				case mtm: ColumnInfoTraversableOneToMany[_, _, _, _, _, _] =>
					(ci.column.alias, if (ll) Nil else vm.valueOf(ci))
				case otor: ColumnInfoOneToOneReverse[_, _, _, _] =>
					(ci.column.alias, if (ll) null else vm.valueOf(ci))
				case _ => (ci.column.alias, vm.valueOf(ci))
			}
		} ::: table.extraColumnInfosPersisted.map { ci =>
			(ci.column.alias, vm.valueOf(ci))
		}).toMap
		val lazyLoadedVM = ValuesMap.fromMap(lazyLoadedMods)
		val constructed = tpe.constructor(selectConfig.data, lazyLoadedVM)
		val proxy = lazyLoadManager.proxyFor(constructed, entity, lazyLoad, vm)
		proxy
	}
	/**
	 * create a mock of the current entity, to avoid cyclic dependencies
	 * doing infinite loops.
	 */
	private def createMock[ID, PC <: DeclaredIds[ID], T](
		data: Option[Any],
		entity: Entity[ID, PC, T],
		mods: scala.collection.Map[String, Any]): T with PC =
		{
			val mockMods = new scala.collection.mutable.HashMap[String, Any] ++ mods
			mockPlugins.foreach {
				_.updateMock(entity, mockMods)
			}
			val tpe = entity.tpe
			val vm = ValuesMap.fromMap(mockMods)
			val preMock = tpe.constructor(data, vm)
			val mock = tpe.constructor(data, ValuesMap.fromEntity(typeManager, tpe, preMock))
			// mark it as mock
			mock.mapperDaoMock = true
			mock
		}

	override def delete[ID, PC <: DeclaredIds[ID], T](entity: Entity[ID, PC, T], id: ID): Unit =
		{
			val ids = Helpers.idToList(id)
			val tpe = entity.tpe
			val table = tpe.table
			val pks = table.primaryKeys
			if (pks.size != ids.size) throw new IllegalArgumentException("number of primary key values don't match number of primary keys : %s != %s".format(pks, ids))
			val keyValues = pks zip ids
			// do the actual delete database op
			driver.doDelete(tpe, keyValues)
		}
	/**
	 * deletes an entity from the database
	 */
	override def delete[ID, PC <: DeclaredIds[ID], T](deleteConfig: DeleteConfig, entity: Entity[ID, PC, T], o: T with PC): T = {
		val entityMap = new UpdateEntityMap
		val deleted = deleteInner(deleteConfig, entity, o, entityMap)
		entityMap.done
		deleted
	}

	private[mapperdao] def deleteInner[ID, PC <: DeclaredIds[ID], T](
		deleteConfig: DeleteConfig,
		entity: Entity[ID, PC, T],
		o: T with PC,
		entityMap: UpdateEntityMap): T = {
		if (o.mapperDaoDiscarded) throw new IllegalArgumentException("can't operate on an object twice. An object that was updated/deleted must be discarded and replaced by the return value of update(), i.e. onew=update(o) or just be disposed if it was deleted. The offending object was : " + o);

		val tpe = entity.tpe
		val table = tpe.table

		try {
			val keyValues0 = table.toListOfPrimaryKeySimpleColumnAndValueTuples(o) ::: beforeDeletePlugins.flatMap(
				_.idColumnValueContribution(tpe, deleteConfig, events, o, entityMap)
			)

			val keyValues = keyValues0 ::: table.toListOfUnusedPrimaryKeySimpleColumnAndValueTuples(o)
			// call all the before-delete plugins
			beforeDeletePlugins.foreach {
				_.before(entity, deleteConfig, events, o, keyValues, entityMap)
			}

			// execute the before-delete events
			events.executeBeforeDeleteEvents(tpe, keyValues, o)

			// do the actual delete database op
			driver.doDelete(tpe, keyValues)

			// execute the after-delete events
			events.executeAfterDeleteEvents(tpe, keyValues, o)

			// return the object
			o
		} catch {
			case e: Throwable => throw new PersistException("An error occured during delete of entity %s with value %s".format(entity, o), e)
		}
	}

	override def unlink[ID, PC <: DeclaredIds[ID], T](entity: Entity[ID, PC, T], o: T): T = {
		val unlinkVisitor = new UnlinkEntityRelationshipVisitor
		unlinkVisitor.visit(entity, o)
		unlinkVisitor.unlink(o)
		o
	}

	override def merge[ID, PC <: DeclaredIds[ID], T](
		selectConfig: SelectConfig,
		updateConfig: UpdateConfig,
		entity: Entity[ID, PC, T],
		o: T,
		ids: ID): T with PC =
		select(selectConfig, entity, ids) match {
			case None => insert(updateConfig, entity, o)
			case Some(oldO) =>
				update(updateConfig, entity, oldO, o)
		}

	/**
	 * ===================================================================================
	 * common methods
	 * ===================================================================================
	 */
	override def toString = "MapperDao(%s)".format(driver)
}
