package com.googlecode.mapperdao.plugins

import com.googlecode.mapperdao.Type
import com.googlecode.mapperdao.MapperDao
import com.googlecode.mapperdao.jdbc.JdbcMap
import com.googlecode.mapperdao.EntityMap
import com.googlecode.mapperdao.ManyToOne
import com.googlecode.mapperdao.SelectConfig

/**
 * @author kostantinos.kougios
 *
 * 31 Aug 2011
 */
class ManyToOneSelectPlugin(mapperDao: MapperDao) extends BeforeSelect with SelectMock {
	private val typeRegistry = mapperDao.typeRegistry

	override def idContribution[PC, T](tpe: Type[PC, T], om: JdbcMap, entities: EntityMap, mods: scala.collection.mutable.HashMap[String, Any]): List[Any] = Nil

	override def before[PC, T](tpe: Type[PC, T], selectConfig: SelectConfig, om: JdbcMap, entities: EntityMap, mods: scala.collection.mutable.HashMap[String, Any]) =
		{
			val table = tpe.table
			// many to one
			table.manyToOneColumnInfos.filterNot(selectConfig.skip(_)).foreach { ci =>
				val c = ci.column.asInstanceOf[ManyToOne[Any]]
				val fe = typeRegistry.entityOf[Any, Any](c.foreign.clz)
				val foreignPKValues = c.columns.map(mtoc => om(mtoc.columnName))
				val fo = entities.get(fe.clz, foreignPKValues)
				val v = if (fo.isDefined) {
					fo.get
				} else {
					entities.down(tpe, ci, om)
					val v = mapperDao.selectInner(fe, selectConfig, foreignPKValues, entities).getOrElse(null)
					entities.up
					v
				}
				mods(c.foreign.alias) = v
			}
		}

	override def updateMock[PC, T](tpe: Type[PC, T], mods: scala.collection.mutable.HashMap[String, Any]) {
		mods ++= tpe.table.manyToOneColumns.map(c => (c.alias -> null))
	}
}