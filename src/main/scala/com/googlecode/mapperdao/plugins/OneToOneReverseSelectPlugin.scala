package com.googlecode.mapperdao.plugins

import com.googlecode.mapperdao.drivers.Driver
import com.googlecode.mapperdao.ColumnInfoOneToOneReverse
import com.googlecode.mapperdao.Entity
import com.googlecode.mapperdao.EntityMap
import com.googlecode.mapperdao.ExternalEntity
import com.googlecode.mapperdao.MapperDaoImpl
import com.googlecode.mapperdao.SelectConfig
import com.googlecode.mapperdao.SelectExternalOneToOneReverse
import com.googlecode.mapperdao.SelectInfo
import com.googlecode.mapperdao.Type
import com.googlecode.mapperdao.TypeRegistry
import com.googlecode.mapperdao.DatabaseValues

/**
 * @author kostantinos.kougios
 *
 * 31 Aug 2011
 */
class OneToOneReverseSelectPlugin(typeRegistry: TypeRegistry, driver: Driver, mapperDao: MapperDaoImpl) extends BeforeSelect {

	override def idContribution[PC, T](tpe: Type[PC, T], om: DatabaseValues, entities: EntityMap) =
		{
			val SelectInfo(parentTpe, parentCI, parentJdbcMap) = entities.peek
			if (parentTpe != null) {
				parentCI match {
					case _: ColumnInfoOneToOneReverse[_, _, _] =>
						// we need to contribute the parent's id's to the entity's id 
						parentTpe.table.primaryKeys.map(c => parentJdbcMap(c.name))
					case _ => Nil
				}
			} else Nil
		}

	override def before[PC, T](entity: Entity[PC, T], selectConfig: SelectConfig, om: DatabaseValues, entities: EntityMap) =
		{
			val tpe = entity.tpe
			val table = tpe.table
			// one to one reverse
			table.oneToOneReverseColumnInfos.filterNot(selectConfig.skip(_)).map { ci =>
				val v = ci.column.foreign.entity match {
					case ee: ExternalEntity[Any] =>
						() => {
							val foreignIds = tpe.table.primaryKeys.map { pk => om(pk.name) }
							ee.oneToOneOnSelectMap(ci.asInstanceOf[ColumnInfoOneToOneReverse[_, _, Any]])(SelectExternalOneToOneReverse(selectConfig, foreignIds))
						}
					case _ =>
						// try to capture as few variables as possible
						// for optimal memory usage
						val down = entities.down(selectConfig, tpe, ci, om)
						new OneToOneReverseEntityLazyLoader(selectConfig, mapperDao, entity, om, down, ci)
				}
				SelectMod(ci.column.foreign.alias, v, null)
			}
		}
}