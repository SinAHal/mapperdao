package com.googlecode.mapperdao.plugins

import com.googlecode.mapperdao.utils.Equality
import com.googlecode.mapperdao.utils.MapOfList
import com.googlecode.mapperdao._
import com.googlecode.mapperdao.utils.Helpers
import com.googlecode.mapperdao.events.Events

/**
 * @author kostantinos.kougios
 *
 * 31 Aug 2011
 */
class ManyToOneSelectPlugin(typeRegistry: TypeRegistry, mapperDao: MapperDaoImpl) extends BeforeSelect with SelectMock {

	override def idContribution[PC, T](tpe: Type[PC, T], om: DatabaseValues, entities: EntityMap) = Nil

	override def before[PC, T](entity: Entity[PC, T], selectConfig: SelectConfig, om: DatabaseValues, entities: EntityMap) =
		{
			val tpe = entity.tpe
			val table = tpe.table
			// many to one
			table.manyToOneColumnInfos.filterNot(selectConfig.skip(_)).map { cis =>
				val v = cis.column.foreign.entity match {
					case ee: ExternalEntity[Any] =>
						() => {
							val c = cis.column
							val foreignPKValues = c.columns.map(mtoc => om(mtoc.name))
							ee.manyToOneOnSelectMap(cis.asInstanceOf[ColumnInfoManyToOne[_, _, Any]])(SelectExternalManyToOne(selectConfig, foreignPKValues))
						}
					case _ =>
						// try to capture as few variables as possible
						// to limit memory usage for lazy loaded
						val c = cis.column
						val fe = c.foreign.entity
						val foreignPKValues = c.columns.map { mtoc =>
							om(mtoc.name)
						}
						entities.justGet[T](fe.clz, foreignPKValues)
							.map { o =>
								() => o
							}.getOrElse {
								val down = entities.down(selectConfig, tpe, cis, om)
								new ManyToOneEntityLazyLoader(mapperDao, selectConfig, cis, down, om)
							}
				}
				SelectMod(cis.column.foreign.alias, v, null)
			}
		}

	override def updateMock[PC, T](entity: Entity[PC, T], mods: scala.collection.mutable.Map[String, Any]) {
		mods ++= entity.tpe.table.manyToOneColumns.map(c => (c.alias -> null))
	}
}