package com.googlecode.mapperdao.state.persistcmds

import com.googlecode.mapperdao._
import utils.TraversableSeparation

/**
 * entities are converted to PersistOps
 *
 * @author kostantinos.kougios
 *
 *         21 Nov 2012
 */
class CmdPhase(typeManager: TypeManager) {

	private var alreadyProcessed = Map[Int, List[PersistCmd]]()

	def toInsertCmd[ID, T](
		tpe: Type[ID, T],
		newVM: ValuesMap,
		updateConfig: UpdateConfig
	) = insert(tpe, newVM, true, updateConfig)

	def toUpdateCmd[ID, T](
		tpe: Type[ID, T],
		oldValuesMap: ValuesMap,
		newValuesMap: ValuesMap,
		updateConfig: UpdateConfig
	) = update(tpe, oldValuesMap, newValuesMap, true, updateConfig)

	private def insert[ID, T](
		tpe: Type[ID, T],
		newVM: ValuesMap,
		mainEntity: Boolean,
		updateConfig: UpdateConfig
	): List[PersistCmd] = {
		alreadyProcessed.get(newVM.identity) match {
			case None =>
				val table = tpe.table
				val columnAndValues = newVM.toListOfSimpleColumnAndValueTuple(table.simpleTypeNotAutoGeneratedColumns)
				val op = InsertCmd(tpe, newVM, columnAndValues, mainEntity) :: related(tpe, None, newVM, updateConfig)
				alreadyProcessed += (newVM.identity -> op)
				op
			case Some(x) =>
				AlreadyProcessedCmd :: Nil
		}
	}

	private def update[ID, T](
		tpe: Type[ID, T],
		oldVM: ValuesMap,
		newVM: ValuesMap,
		mainEntity: Boolean,
		updateConfig: UpdateConfig
	): List[PersistCmd] = {
		val op = alreadyProcessed.get(newVM.identity)
		if (op.isDefined) {
			AlreadyProcessedCmd :: Nil
		} else {
			val table = tpe.table
			val newColumnAndValues = newVM.toListOfColumnAndValueTuple(table.simpleTypeNotAutoGeneratedColumns)
			val oldColumnAndValues = oldVM.toListOfColumnAndValueTuple(table.simpleTypeNotAutoGeneratedColumns)
			val changedColumnAndValues = (newColumnAndValues zip oldColumnAndValues) collect {
				case ((nc, nv), (oc, ov)) if (nv != ov) => (nc, nv)
			}
			val rel = related(tpe, Some(oldVM), newVM, updateConfig)
			val op = UpdateCmd(tpe, oldVM, newVM, changedColumnAndValues, mainEntity) :: rel
			alreadyProcessed += (newVM.identity -> op)
			op
		}
	}

	private def related[ID, T](
		tpe: Type[ID, T],
		oldVMO: Option[ValuesMap],
		newVM: ValuesMap,
		updateConfig: UpdateConfig
	): List[PersistCmd] = {
		tpe.table.relationshipColumnInfos(updateConfig.skip).map {
			/**
			 * ---------------------------------------------------------------------------------------------
			 * Many-To-Many
			 * ---------------------------------------------------------------------------------------------
			 */
			case ci@ColumnInfoTraversableManyToMany(column, columnToValue, _) =>
				val foreignEntity = column.foreign.entity
				foreignEntity match {
					/**
					 * ---------------------------------------------------------------------------------------------
					 * Many-To-Many : External entity
					 * ---------------------------------------------------------------------------------------------
					 */
					case foreignEE: ExternalEntity[_, _] =>
						if (oldVMO.isDefined) {
							// entity is updated
							val oldVM = oldVMO.get
							val oldT = oldVM.manyToMany(column)
							val newT = newVM.manyToMany(column)
							// we'll find what was added, intersect (stayed in the collection but might have been updated)
							// and removed from the collection
							val (added, intersect, removed) = TraversableSeparation.separate(foreignEntity, oldT, newT)

							val addedCmds = added.toList.map {
								fo =>
									InsertManyToManyExternalCmd(
										tpe,
										foreignEE,
										ci.asInstanceOf[ColumnInfoTraversableManyToMany[T, Any, Any]],
										newVM,
										fo)
							}
							val intersectCmds = intersect.toList.map {
								case (oldO, newO) =>
									UpdateExternalCmd(foreignEE, ci.asInstanceOf[ColumnInfoTraversableManyToMany[T, Any, Any]], newO)
							}
							val removedCmds = removed.toList.map {
								ro =>
									DeleteManyToManyExternalCmd(tpe, foreignEE, ci.asInstanceOf[ColumnInfoTraversableManyToMany[T, Any, Any]], newVM, ro)
							}
							addedCmds ::: intersectCmds ::: removedCmds
						} else {
							newVM.manyToMany(column).map {
								fo =>
									InsertManyToManyExternalCmd(
										tpe,
										foreignEE,
										ci.asInstanceOf[ColumnInfoTraversableManyToMany[T, Any, Any]],
										newVM,
										fo)
							}
						}

					/**
					 * ---------------------------------------------------------------------------------------------
					 * Many-To-Many : Normal entity
					 * ---------------------------------------------------------------------------------------------
					 */
					case _ =>
						if (oldVMO.isDefined) {
							// entity is updated
							val oldVM = oldVMO.get
							val oldT = oldVM.manyToMany(column)
							val newT = newVM.manyToMany(column)
							// we'll find what was added, intersect (stayed in the collection but might have been updated)
							// and removed from the collection
							val (added, intersect, removed) = TraversableSeparation.separate(foreignEntity, oldT, newT)

							val addedCmds = added.toList.map {
								fo =>
									val foCmds = insertOrUpdate(foreignEntity.tpe, fo, updateConfig)
									val foreignVM = findVM(foCmds, fo)
									InsertManyToManyCmd(
										tpe,
										foreignEntity.tpe,
										column,
										newVM,
										foreignVM) :: foCmds
							}.flatten
							val removedCms = removed.toList.map {
								fo =>
									val foreignVM = ValuesMap.fromType(typeManager, foreignEntity.tpe, fo)
									DeleteManyToManyCmd(
										tpe,
										foreignEntity.tpe,
										column,
										oldVM,
										foreignVM
									)
							}

							val intersectCmds = intersect.toList.map {
								case (oldO, newO) =>
									val oVM = oldO match {
										case p: Persisted => p.mapperDaoValuesMap
									}
									val nVM = newO match {
										case p: Persisted => p.mapperDaoValuesMap
										case no =>
											ValuesMap.fromType(typeManager, foreignEntity.tpe, no)
									}
									update(foreignEntity.tpe, oVM, nVM, false, updateConfig)
							}.flatten
							addedCmds ::: removedCms ::: intersectCmds
						} else {
							// entity is new
							newVM.manyToMany(column).map {
								case p: DeclaredIds[Any] =>
									// we need to link to the already existing foreign entity
									// and update the foreign entity
									val foreignVM = ValuesMap.fromType(typeManager, foreignEntity.tpe, p)
									InsertManyToManyCmd(
										tpe,
										foreignEntity.tpe,
										column,
										newVM,
										foreignVM) :: doUpdate(foreignEntity.tpe, p, updateConfig)
								case o =>
									// we need to insert the foreign entity and link to entity
									val foreignVM = ValuesMap.fromType(typeManager, foreignEntity.tpe, o)
									InsertManyToManyCmd(
										tpe,
										foreignEntity.tpe,
										column,
										newVM,
										foreignVM) :: insert(foreignEntity.tpe, foreignVM, false, updateConfig)
							}.flatten
						}
				}

			/**
			 * ---------------------------------------------------------------------------------------------
			 * Many-To-One
			 * ---------------------------------------------------------------------------------------------
			 */
			case ci@ColumnInfoManyToOne(column, columnToValue, _) =>
				val fo = newVM.manyToOne(column)
				column.foreign.entity match {
					case foreignEE: ExternalEntity[_, _] =>
						if (oldVMO.isDefined) {
							// update
							Nil
						} else {
							// insert
							val foreignTpe = foreignEE.tpe
							val ie = InsertExternalManyToOne(updateConfig, newVM, fo)
							val v = foreignEE.manyToOneOnInsertMap(ci)(ie)
							ExternalEntityRelatedCmd(column, newVM, foreignTpe, v) :: Nil
						}
					case foreignEntity =>
						val foreignTpe = foreignEntity.tpe
						if (fo == null) {
							EntityRelatedCmd(column, newVM, foreignTpe, null) :: Nil
						} else {
							// insert new
							val foreignVM = ValuesMap.fromType(typeManager, foreignTpe, fo)
							EntityRelatedCmd(column, newVM, foreignTpe, foreignVM) :: (fo match {
								case p: DeclaredIds[_] =>
									doUpdate(foreignTpe.asInstanceOf[Type[Any, Any]], p.asInstanceOf[Any with DeclaredIds[Any]], updateConfig)
								case _ =>
									// we need to insert the foreign entity and link to entity
									insert(foreignTpe, foreignVM, false, updateConfig)
							})
						}
				}
		}.flatten
	}

	private def insertOrUpdate[ID, T](tpe: Type[ID, T], o: T, updateConfig: UpdateConfig) = o match {
		case p: T with DeclaredIds[ID] => doUpdate(tpe, p, updateConfig)
		case _ => doInsert(tpe, o, updateConfig)
	}

	private def doInsert[ID, T](tpe: Type[ID, T], o: T, updateConfig: UpdateConfig) = {
		val newVM = ValuesMap.fromType(typeManager, tpe, o)
		insert(tpe, newVM, false, updateConfig)
	}

	private def doUpdate[ID, T](tpe: Type[ID, T], p: T with DeclaredIds[ID], updateConfig: UpdateConfig) = {
		val newVM = ValuesMap.fromType(typeManager, tpe, p)
		update(tpe, p.mapperDaoValuesMap, newVM, false, updateConfig)
	}

	def findVM(cmds: List[PersistCmd], fo: Any) = cmds.head match {
		case wvm: CmdWithNewVM => val vm = wvm.newVM
		if (vm.identity != System.identityHashCode(fo)) throw new IllegalStateException("didn't find correct VM for " + fo)
		vm
	}
}