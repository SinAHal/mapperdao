package com.googlecode.mapperdao.plugins

import com.googlecode.mapperdao._

class OneToManyDeletePlugin(typeRegistry: TypeRegistry, mapperDao: MapperDaoImpl) extends BeforeDelete {

	override def idColumnValueContribution[ID, T](
		tpe: Type[ID, T],
		deleteConfig: DeleteConfig,
		o: T with DeclaredIds[ID],
		entityMap: UpdateEntityMap
	): List[(SimpleColumn, Any)] = {
		val UpdateInfo(parentO, ci, parentEntity) = entityMap.peek[Any, Any, Traversable[T], Any, T]
		ci match {
			case oneToMany: ColumnInfoTraversableOneToMany[_, _, _, T] =>
				val parentTpe = parentEntity.tpe
				oneToMany.column.foreignColumns zip parentTpe.table.toListOfPrimaryKeyValues(parentO)
			case _ => Nil
		}
	}

	override def before[ID, T](
		entity: Entity[ID, T],
		deleteConfig: DeleteConfig,
		o: T with DeclaredIds[ID],
		keyValues: List[(ColumnBase, Any)],
		entityMap: UpdateEntityMap
	) =
		if (deleteConfig.propagate) {
			val tpe = entity.tpe
			tpe.table.oneToManyColumnInfos.filterNot(deleteConfig.skip(_)).foreach {
				cis =>

					val fOTraversable = cis.columnToValue(o)

					cis.column.foreign.entity match {
						case ee: ExternalEntity[Any, Any] =>
							val handler = ee.oneToManyOnDeleteMap(cis.asInstanceOf[ColumnInfoTraversableOneToMany[_, T, _, Any]])
								.asInstanceOf[ee.OnDeleteOneToMany[T]]
							handler(DeleteExternalOneToMany(deleteConfig, o, fOTraversable))

						case fe: Entity[Any, Any] =>
							if (fOTraversable != null) fOTraversable.foreach {
								fO =>
									val fOPersisted = fO.asInstanceOf[DeclaredIds[Any]]
									if (!fOPersisted.mapperDaoMock) {
										mapperDao.delete(deleteConfig, fe, fOPersisted)
									}
							}
					}
			}
		}
}