package com.rits.orm.plugins

import com.rits.orm.Type
import com.rits.orm.MapperDao
import com.rits.jdbc.JdbcMap
import com.rits.orm.EntityMap

/**
 * @author kostantinos.kougios
 *
 * 31 Aug 2011
 */
class OneToOneReverseSelectPlugin(mapperDao: MapperDao) extends BeforeSelect {
	private val typeRegistry = mapperDao.typeRegistry
	private val driver = mapperDao.driver

	override def idContribution[PC, T](tpe: Type[PC, T], om: JdbcMap, entities: EntityMap, mods: scala.collection.mutable.HashMap[String, Any]): List[Any] =
		{
			val table = tpe.table
			table.oneToOneColumnInfos.map { ci =>
				println(ci)
			}
		}

	override def before[PC, T](tpe: Type[PC, T], om: JdbcMap, entities: EntityMap, mods: scala.collection.mutable.HashMap[String, Any]) =
		{
			val table = tpe.table
			// one to one reverse
			table.oneToOneReverseColumnInfos.foreach { ci =>
				val c = ci.column
				val ftpe = typeRegistry.typeOf(c.foreign.clz)
				val ids = tpe.table.primaryKeys.map { pk => om(pk.column.columnName) }
				val fom = driver.doSelect(ftpe, c.foreignColumns.zip(ids))
				entities.down(tpe, ci)
				val otmL = mapperDao.toEntities(fom, ftpe, entities)
				entities.up
				if (otmL.isEmpty) {
					mods(c.foreign.alias) = null
				} else {
					if (otmL.size > 1) throw new IllegalStateException("expected 0 or 1 row but got " + otmL)
					else {
						mods(c.foreign.alias) = otmL.head
					}
				}
			}
		}
}