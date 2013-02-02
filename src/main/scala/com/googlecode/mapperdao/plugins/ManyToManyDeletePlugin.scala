package com.googlecode.mapperdao.plugins

import com.googlecode.mapperdao.drivers.Driver
import com.googlecode.mapperdao._

class ManyToManyDeletePlugin(driver: Driver, mapperDao: MapperDaoImpl) extends BeforeDelete {

	override def idColumnValueContribution[ID, T](
		tpe: Type[ID, T],
		deleteConfig: DeleteConfig,
		o: T with DeclaredIds[ID],
		entityMap: UpdateEntityMap
		): List[(SimpleColumn, Any)] = Nil

	override def before[ID, T](
		entity: Entity[ID, T],
		deleteConfig: DeleteConfig,
		o: T with DeclaredIds[ID],
		keyValues: List[(ColumnBase, Any)],
		entityMap: UpdateEntityMap
		) =
		if (deleteConfig.propagate) {
			val tpe = entity.tpe
			tpe.table.manyToManyColumnInfos.filterNot(deleteConfig.skip(_)).foreach {
				ci =>
					driver.doDeleteAllManyToManyRef(tpe, ci.column, keyValues.map(_._2))

					ci.column.foreign.entity match {
						case ee: ExternalEntity[Any, Any] =>
							val fos = ci.columnToValue(o)

							fos.foreach {
								fo =>
									val de = DeleteExternalManyToMany(deleteConfig, fo)
									ee.manyToManyOnUpdateMap(ci.asInstanceOf[ColumnInfoTraversableManyToMany[T, Any, Any]])(de)
							}
						case _ =>
					}
			}
		}
}