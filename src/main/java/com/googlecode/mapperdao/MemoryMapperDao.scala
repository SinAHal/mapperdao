package com.googlecode.mapperdao
import com.googlecode.mapperdao.jdbc.JdbcMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import exceptions.PersistException

/**
 * a memory implementation of the MapperDao interface, useful for testing
 * or prototype creation.
 *
 * This implementation stores any inserted entities into a map, based on their
 * class and id. Updates update the stored entity and selects retrieve the
 * entity from the map.
 *
 * QueryDao will not function if it is configured to use this implementation.
 *
 * Thread safe.
 *
 * @author kostantinos.kougios
 *
 * 11 Oct 2011
 */
class MemoryMapperDao(typeRegistry: TypeRegistry, typeManager: TypeManager) extends MapperDao {

	if (typeRegistry == null) throw new NullPointerException("typeRegistry is null")
	if (typeManager == null) throw new NullPointerException("typeManager is null")

	private val idGen = new AtomicLong
	private val m = new ConcurrentHashMap[List[Any], Persisted]

	// insert
	def insert[PC, T](updateConfig: UpdateConfig, entity: Entity[PC, T], o: T): T with PC =
		{
			if (o == null) throw new NullPointerException("o must not be null")
			if (entity == null) throw new NullPointerException("entity must not be null")

			val tpe = entity.tpe
			val modified = ValuesMap.fromEntity(typeManager, tpe, o).toLowerCaseMutableMap

			val table = tpe.table
			val vm = ValuesMap.fromEntity(typeManager, tpe, o)
			table.simpleTypeAutoGeneratedColumns.foreach { c =>
				modified(c.columnName) = idGen.incrementAndGet
			}
			val e = tpe.constructor(ValuesMap.fromMap(typeManager, modified.cloneMap))
			val pks = table.toListOfPrimaryKeyValues(e)
			val key = entity.clz :: pks
			if (m.containsKey(key)) throw new PersistException("Primary Key violation: key %s already persisted".format(key))
			m.put(key, e)
			e
		}

	// update
	def update[PC, T](updateConfig: UpdateConfig, entity: Entity[PC, T], o: T with PC): T with PC = {
		if (o == null) throw new NullPointerException("o must not be null")
		if (entity == null) throw new NullPointerException("entity must not be null")
		val tpe = entity.tpe
		val modified = ValuesMap.fromEntity(typeManager, tpe, o).toLowerCaseMutableMap
		val table = tpe.table
		val pks = table.toListOfPrimaryKeyValues(o)
		val key = entity.clz :: pks
		if (!m.containsKey(key)) throw new PersistException("entity with key %s not persisted: %s".format(key, o))
		val e = tpe.constructor(ValuesMap.fromMap(typeManager, modified.cloneMap))
		e
	}
	// update immutable
	def update[PC, T](updateConfig: UpdateConfig, entity: Entity[PC, T], o: T with PC, newO: T): T with PC = {
		if (o == null) throw new NullPointerException("o must not be null")
		if (entity == null) throw new NullPointerException("entity must not be null")
		val tpe = entity.tpe
		val modified = ValuesMap.fromEntity(typeManager, tpe, o).toLowerCaseMutableMap.cloneMap ++ ValuesMap.fromEntity(typeManager, tpe, newO).toLowerCaseMutableMap.cloneMap
		val table = tpe.table
		val pks = table.toListOfPrimaryKeyValues(o)
		val key = entity.clz :: pks
		if (!m.containsKey(key)) throw new PersistException("entity with key %s not persisted: %s".format(key, o))
		val e = tpe.constructor(ValuesMap.fromMap(typeManager, modified))
		m.put(key, e)
		e
	}

	// select
	def select[PC, T](selectConfig: SelectConfig, entity: Entity[PC, T], ids: List[Any]): Option[T with PC] = {
		val key = entity.clz :: ids
		val e = m.get(key)
		if (e == null) None else {
			val tpe = entity.tpe
			val modified = ValuesMap.fromEntity(typeManager, tpe, e.asInstanceOf[T]).toLowerCaseMutableMap
			Some(tpe.constructor(ValuesMap.fromMap(typeManager, modified.cloneMap)))
		}
	}

	// delete
	def delete[PC, T](deleteConfig: DeleteConfig, entity: Entity[PC, T], o: T with PC): T = {
		val tpe = entity.tpe
		val table = tpe.table
		val pks = table.toListOfPrimaryKeyValues(o)
		val key = entity.clz :: pks
		m.remove(key)
		o
	}

	def delete[PC, T](entity: Entity[PC, T], ids: List[AnyVal]): Unit = {
		val tpe = entity.tpe
		val table = tpe.table
		val pks = table.primaryKeyColumns
		if (pks.size != ids.size) throw new IllegalArgumentException("number of primary key values don't match number of primary keys : %s != %s".format(pks, ids))
		val key = entity.clz :: ids
		m.remove(key)
	}

	override def toString = "MemoryMapperDao(%s)".format(m)
}

object MemoryMapperDao {
	// a factory to create a MemoryMapperDao with the default TypeManager
	def apply(typeRegistry: TypeRegistry): MapperDao = new MemoryMapperDao(typeRegistry, new DefaultTypeManager)
	def apply(typeRegistry: TypeRegistry, typeManager: TypeManager): MapperDao = new MemoryMapperDao(typeRegistry, typeManager)
}