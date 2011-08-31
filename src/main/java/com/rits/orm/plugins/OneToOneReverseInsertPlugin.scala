package com.rits.orm.plugins

import com.rits.orm.UpdateEntityMap
import com.rits.orm.Type
import com.rits.orm.MapperDao
import com.rits.orm.Persisted
import com.rits.orm.utils.MapOfList

/**
 * @author kostantinos.kougios
 *
 * 31 Aug 2011
 */
class OneToOneReverseInsertPlugin(mapperDao: MapperDao) extends PostInsert {
	val typeRegistry = mapperDao.typeRegistry

	override def after[PC, T](tpe: Type[PC, T], o: T, mockO: T with PC, entityMap: UpdateEntityMap, modified: scala.collection.mutable.Map[String, Any], modifiedTraversables: MapOfList[String, Any]): Unit =
		{
			val table = tpe.table
			// one-to-one reverse
			table.oneToOneReverseColumnInfos.foreach { cis =>
				val fo = cis.columnToValue(o)
				val v = if (fo != null) {
					val fe = typeRegistry.entityOfObject[Any, Any](fo)
					fo match {
						case null => null
						case p: Persisted =>
							entityMap.down(mockO, cis)
							val updated = mapperDao.updateInner(fe, p, entityMap)
							entityMap.up
							updated
						case x =>
							entityMap.down(mockO, cis)
							val inserted = mapperDao.insertInner(fe, x, entityMap)
							entityMap.up
							inserted
					}
				} else null
				modified(cis.column.alias) = v
			}
		}
}