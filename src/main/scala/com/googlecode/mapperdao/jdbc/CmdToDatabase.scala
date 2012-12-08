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

	def insert[ID, PC <: DeclaredIds[ID], T](
		cmds: List[PersistCmd[ID, PC, T]]): List[T with PC] = {
		// collect the sql and values
		val nodes = cmds.map { cmd =>
			val sql = toSql(cmd)
			PersistedNode(sql.sql, sql.values, cmd.entity, cmd.o, Nil, Nil, None)
		}

		// run the batch updates
		nodes.groupBy(_.sql).foreach {
			case (sql, nodes) =>
				val entity = nodes.head.entity
				val table = entity.tpe.table
				val autoGeneratedColumnNames = table.autoGeneratedColumnNamesArray
				val bo = BatchOptions(driver.batchStrategy, autoGeneratedColumnNames)
				val args = nodes.map {
					case PersistedNode(_, values, _, _, _, _, _) =>
						values.toArray
				}.toArray

				// do the batch update
				val br = jdbc.batchUpdate(bo, sql, args)

				// now extract the keys and set them into the nodes
				val keys = br.keys map { m =>
					table.autoGeneratedColumns.map { column =>
						(column, driver.getAutoGenerated(m, column))
					}
				}
				(nodes zip keys) foreach {
					case (node, key) => node.keys = key
				}
				nodes
		}

		// reconstruct the persisted entities
		val entities = nodes.map { node =>
			val entity = node.entity
			val newM = ValuesMap.entityToMap(typeManager, entity.tpe, node.o, false) ++ node.keysToMap
			val newVM = ValuesMap.fromMap(newM)
			entity.constructor(updateConfig.data, newVM)
		}
		entities
	}

	private def toSql(cmd: PersistCmd[_, _, _]) = cmd match {
		case InsertCmd(entity, o, columns, commands) =>
			driver.insertSql(entity.tpe, columns).result
	}
}