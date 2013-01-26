package com.googlecode.mapperdao.jdbc

import com.googlecode.mapperdao._
import com.googlecode.mapperdao.state.persistcmds._
import com.googlecode.mapperdao.drivers.Driver
import com.googlecode.mapperdao.state.persisted._
import com.googlecode.mapperdao.state.persistcmds.PersistCmd
import com.googlecode.mapperdao.state.persistcmds.InsertCmd
import state.prioritise.Prioritized

/**
 * converts commands to database operations, executes
 * them and returns the resulting persisted nodes.
 *
 * @author kostantinos.kougios
 *
 *         22 Nov 2012
 */
class CmdToDatabase(
	updateConfig: UpdateConfig,
	protected val driver: Driver,
	typeManager: TypeManager,
	prioritized: Prioritized
	) {

	private val jdbc = driver.jdbc

	// keep track of which entities were already persisted in order to
	// know if a depended entity can be persisted
	private val persistedIdentities = scala.collection.mutable.HashSet[Int]()

	val dependentMap = prioritized.dependent.groupBy(_.identity).map {
		case (identity, l) =>
			(identity, l.map {
				_.dependsOnIdentity
			}.toSet)
	}

	// true if all needed related entities are already persisted.
	private def allDependenciesAlreadyPersisted(identity: Int) = dependentMap.get(identity) match {
		case None => true
		case Some(set) => set.forall(persistedIdentities(_))
	}

	private case class Node(
		sql: driver.sqlBuilder.Result,
		cmd: PersistCmd
		)

	def execute: List[PersistedNode[_, _]] = {

		// we need to flatten out the sql's so that we can batch process them
		// but also keep the tree structure so that we return only PersistedNode's
		// for the top level PersistedCmd's

		val cmdList = (prioritized.high ::: List(prioritized.low))

		/**
		 * cmdList contains a list of prioritized PersistCmd, according to their
		 * relevant entity priority. Some times related entities still are
		 * scheduled to be persisted before the entity that references them.
		 * i.e. a one-to-many Person(name,Set[Person])
		 *
		 * We need to make sure that all entities are persisted in the
		 * correct order.
		 */
		def persist(cmdList: List[List[PersistCmd]], depth: Int) {
			if (depth > 100) throw new IllegalStateException("after 100 iterations, there are still unpersisted entities. Maybe a mapperdao bug. Entities remaining : " + cmdList)
			val remaining = cmdList.map {
				cmds =>
					val (toProcess, remaining) = findToProcess(cmds)
					val nodes = toNodes(toProcess)
					toDb(nodes)
					remaining
			}.filterNot(_.isEmpty)
			if (!remaining.isEmpty) persist(remaining, depth + 1)
		}

		persist(cmdList, 0)

		cmdList.map {
			cmds =>
			// now the batches were executed and we got a tree with
			// the commands and the autogenerated keys.
				toPersistedNodes(cmds)
		}.flatten
	}

	private def findToProcess(cmds: List[PersistCmd]) = cmds.partition {
		case c: CmdWithNewVM =>
			allDependenciesAlreadyPersisted(c.newVM.identity)
		case _ => true
	}

	private def toDb(nodes: List[Node]) {
		// group the sql's and batch-execute them
		nodes.groupBy {
			_.sql.sql
		}.foreach {
			case (sql, nodes) =>
				val cmd = nodes.head.cmd
				val args = nodes.map {
					case Node(sql, _) =>
						sql.values.toArray
				}.toArray

				cmd match {
					case cmdWe: CmdWithType[_, _] =>
						val tpe = cmdWe.tpe
						val table = tpe.table
						val autoGeneratedColumnNames = cmd match {
							case InsertCmd(_, _, _, _) =>
								table.autoGeneratedColumnNamesArray
							case _ => Array[String]()
						}
						val bo = BatchOptions(driver.batchStrategy(autoGeneratedColumnNames.length > 0), autoGeneratedColumnNames)

						// do the batch update
						val br = jdbc.batchUpdate(bo, sql, args)

						// now extract the keys and set them into the nodes
						if (br.keys != null) {
							val keys: Array[List[(SimpleColumn, Any)]] = br.keys.map {
								m: java.util.Map[String, Object] =>
									table.autoGeneratedColumns.map {
										column =>
											(
												column,
												table.pcColumnToColumnInfoMap(column) match {
													case ci: ColumnInfo[_, _] =>
														val value = driver.getAutoGenerated(m, column)
														typeManager.toActualType(ci.dataType, value)
												}
												)
									}
							}
							// note down the generated keys
							(nodes zip keys) foreach {
								case (node, keys) =>
									node.cmd match {
										case InsertCmd(_, newVM, _, _) =>
											newVM.addAutogeneratedKeys(keys)
									}
							}
						}
					case InsertManyToManyCmd(_, _, _, _, _) =>
						val bo = BatchOptions(driver.batchStrategy(false), Array())
						jdbc.batchUpdate(bo, sql, args)
					case DeleteManyToManyCmd(entity, foreignEntity, manyToMany, entityVM, foreignEntityVM) =>
						val bo = BatchOptions(driver.batchStrategy(false), Array())
						jdbc.batchUpdate(bo, sql, args)
					case InsertManyToManyExternalCmd(_, _, _, _, _) | DeleteManyToManyExternalCmd(_, _, _, _, _) =>
						val bo = BatchOptions(driver.batchStrategy(false), Array())
						jdbc.batchUpdate(bo, sql, args)
				}
		}
	}

	private def toPersistedNodes(nodes: List[PersistCmd]) = nodes.collect {
		case InsertCmd(tpe, newVM, _, mainEntity) =>
			EntityPersistedNode(tpe, None, newVM, mainEntity)
		case UpdateCmd(tpe, oldVM, newVM, _, mainEntity) =>
			EntityPersistedNode(tpe, Some(oldVM), newVM, mainEntity)
		case InsertManyToManyExternalCmd(tpe, foreignEntity, manyToMany, entityVM, foreignO) =>
			ExternalEntityPersistedNode(foreignEntity, foreignO)
		case UpdateExternalManyToManyCmd(foreignEntity, manyToMany, fo) =>
			val ue = UpdateExternalManyToMany(updateConfig, UpdateExternalManyToMany.Operation.Update, fo)
			foreignEntity.manyToManyOnUpdateMap(manyToMany)(ue)
			ExternalEntityPersistedNode(foreignEntity, fo)
		case UpdateExternalManyToOneCmd(foreignEntity, fo) =>
			ExternalEntityPersistedNode(foreignEntity, fo)
	}

	private def toNodes(cmds: List[PersistCmd]) =
		cmds.map {
			cmd =>
				toSql(cmd).map {
					sql =>
						Node(
							sql,
							cmd
						)
				}
		}.filter(_ != None).map(_.get)

	protected[jdbc] def toSql(cmd: PersistCmd) =
		cmd match {
			case ic@InsertCmd(tpe, newVM, columns, _) =>
				persistedIdentities += ic.identity
				Some(driver.insertSql(tpe, columns ::: prioritized.relatedColumns(newVM, false).distinct).result)

			case uc@UpdateCmd(tpe, oldVM, newVM, columns, _) =>
				persistedIdentities += uc.identity
				val oldRelated = prioritized.relatedColumns(oldVM, true)
				val newRelated = prioritized.relatedColumns(newVM, false)
				val set = columns ::: newRelated.filterNot(n => oldRelated.contains(n))
				if (set.isEmpty)
					None
				else {
					val pks = oldVM.toListOfPrimaryKeyAndValueTuple(tpe)
					val relKeys = prioritized.relatedKeys(newVM)
					Some(driver.updateSql(tpe, set, pks ::: relKeys).result)
				}

			case InsertManyToManyCmd(tpe, foreignTpe, manyToMany, entityVM, foreignEntityVM) =>
				val left = entityVM.toListOfPrimaryKeys(tpe)
				val right = foreignEntityVM.toListOfPrimaryKeys(foreignTpe)
				Some(driver.insertManyToManySql(manyToMany, left, right).result)

			case DeleteManyToManyCmd(tpe, foreignTpe, manyToMany, entityVM, foreignEntityVM) =>
				val left = entityVM.toListOfPrimaryKeys(tpe)
				val right = foreignEntityVM.toListOfPrimaryKeys(foreignTpe)
				Some(driver.deleteManyToManySql(manyToMany, left, right).result)

			case dc@DeleteCmd(tpe, vm) =>
				persistedIdentities += dc.identity
				val args = vm.toListOfPrimaryKeyAndValueTuple(tpe)
				Some(driver.deleteSql(tpe, args).result)

			case InsertManyToManyExternalCmd(tpe, foreignEntity, manyToMany, entityVM, fo) =>
				val left = entityVM.toListOfPrimaryKeys(tpe)
				val ie = InsertExternalManyToMany(updateConfig, fo)
				val right = foreignEntity.manyToManyOnInsertMap(manyToMany)(ie)
				Some(driver.insertManyToManySql(manyToMany.column, left, right.values).result)

			case DeleteManyToManyExternalCmd(tpe, foreignEntity, manyToMany, entityVM, fo) =>
				val left = entityVM.toListOfPrimaryKeys(tpe)
				val de = UpdateExternalManyToMany(updateConfig, UpdateExternalManyToMany.Operation.Remove, fo)
				val right = foreignEntity.manyToManyOnUpdateMap(manyToMany)(de)
				Some(driver.deleteManyToManySql(manyToMany.column, left, right.values).result)
			case UpdateExternalManyToOneCmd(_, _) =>
				None
			case UpdateExternalManyToManyCmd(_, _, _) =>
				None
		}
}