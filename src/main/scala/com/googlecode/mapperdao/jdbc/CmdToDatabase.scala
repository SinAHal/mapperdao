package com.googlecode.mapperdao.jdbc

import com.googlecode.mapperdao._
import com.googlecode.mapperdao.state.persistcmds._
import com.googlecode.mapperdao.drivers.Driver
import org.springframework.jdbc.core.SqlParameterValue
import com.googlecode.mapperdao.state.persisted._
import com.googlecode.mapperdao.state.persistcmds.PersistCmd
import com.googlecode.mapperdao.state.persistcmds.InsertCmd

/**
 * converts commands to database operations, executes
 * them and returns the resulting persisted nodes.
 *
 * @author kostantinos.kougios
 *
 * 22 Nov 2012
 */
class CmdToDatabase(
		updateConfig: UpdateConfig,
		driver: Driver,
		typeManager: TypeManager) {

	private val jdbc = driver.jdbc

	private case class Node(
		sql: driver.sqlBuilder.Result,
		cmd: PersistCmd,
		var keys: List[(SimpleColumn, Any)])

	def execute(cmdList: List[List[PersistCmd]]): List[PersistedNode[_, _]] = {

		// we need to flatten out the sql's so that we can batch process them
		// but also keep the tree structure so that we return only PersistedNode's
		// for the top level PersistedCmd's

		cmdList.map { cmds =>
			val nodes = toNodes(cmds)
			toDb(nodes)

			// now the batches were executed and we got a tree with 
			// the commands and the autogenerated keys.
			toPersistedNodes(nodes)
		}.flatten
	}

	private def toDb(nodes: List[Node]) = {
		// group the sql's and batch-execute them
		nodes.groupBy {
			_.sql.sql
		}.foreach {
			case (sql, nodes) =>
				val cmd = nodes.head.cmd

				cmd match {
					case cmdWe: CmdWithEntity[_, _] =>
						val entity = cmdWe.entity
						val table = entity.tpe.table
						val autoGeneratedColumnNames = cmd match {
							case _: InsertCmd[_, _] =>
								table.autoGeneratedColumnNamesArray
							case _ => Array[String]()
						}
						val bo = BatchOptions(driver.batchStrategy, autoGeneratedColumnNames)
						val args = nodes.map {
							case Node(sql, _, _) =>
								sql.values.toArray
						}.toArray

						// do the batch update
						val br = jdbc.batchUpdate(bo, sql, args)

						// now extract the keys and set them into the nodes
						if (br.keys != null) {
							val keys = br.keys map { m =>
								table.autoGeneratedColumns.map { column =>
									(column, driver.getAutoGenerated(m, column))
								}
							}
							// note down the generated keys
							(nodes zip keys) foreach {
								case (node, keys) =>
									node.keys = keys
							}
						}
				}
		}
	}

	private def toPersistedNodes(nodes: List[Node]) = nodes.map { node =>
		val cmd = node.cmd
		cmd match {
			case i: InsertCmd[_, _] =>
				PersistedNode(i.entity, null, i.newVM, Nil, node.keys)
			case u: UpdateCmd[_, _] =>
				PersistedNode(u.entity, u.oldVM, u.newVM, Nil, node.keys)
		}
	}

	private def toNodes(
		cmds: List[PersistCmd]) = cmds.map { cmd =>

		val sql = toSql(cmd)
		Node(
			sql,
			cmd,
			Nil
		)
	}

	private def toSql(cmd: PersistCmd) = cmd match {
		case InsertCmd(entity, o, columns) =>
			driver.insertSql(entity, columns).result
		case UpdateCmd(entity, oldVM, newVM, columns) =>
			val pks = oldVM.toListOfPrimaryKeyAndValueTuple(entity)
			driver.updateSql(entity, columns, pks).result
		case InsertManyToManyCmd(entity, foreignEntity, manyToMany, entityVM, foreignEntityVM) =>
			val left = entityVM.toListOfPrimaryKeys(entity)
			val right = foreignEntityVM.toListOfPrimaryKeys(entity)
			driver.insertManyToManySql(manyToMany, left, right).result
	}
}