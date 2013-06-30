package com.googlecode.mapperdao.plugins

import com.googlecode.mapperdao.drivers.Driver
import com.googlecode.mapperdao._
import com.googlecode.mapperdao.internal.EntityMap
import com.googlecode.mapperdao.jdbc.impl.MapperDaoImpl
import com.googlecode.mapperdao.schema.Type
import com.googlecode.mapperdao.jdbc.DatabaseValues

/**
 * @author kostantinos.kougios
 *
 *         31 Aug 2011
 */
class OneToOneSelectPlugin(typeRegistry: TypeRegistry, driver: Driver, mapperDao: MapperDaoImpl) extends BeforeSelect
{

	override def idContribution[ID, T](
		tpe: Type[ID, T],
		om: DatabaseValues,
		entities: EntityMap
		) = Nil

	override def before[ID, T](
		entity: EntityBase[ID, T],
		selectConfig: SelectConfig,
		databaseValues: DatabaseValues,
		entities: EntityMap
		) = {
		val tpe = entity.tpe
		val table = tpe.table
		// one to one
		table.oneToOneColumnInfos.filterNot(selectConfig.skip(_)).map {
			ci =>
				val c = ci.column
				val foreignKeyValues = c.selfColumns.map(sc => databaseValues(sc))
				val v = if (foreignKeyValues.contains(null)) {
					// value is null
					() => null
				} else {
					val down = entities.down(selectConfig, tpe, ci, databaseValues)
					new OneToOneEntityLazyLoader(selectConfig, mapperDao, down, ci, foreignKeyValues, databaseValues)
				}
				SelectMod(c.foreign.alias, v, null)
		}
	}
}
