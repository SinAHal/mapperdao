package com.googlecode.mapperdao.jdbc

import com.googlecode.mapperdao._
import com.googlecode.mapperdao.ops._
import com.googlecode.mapperdao.drivers.Driver

/**
 * converts commands to database operations, executes
 * them and returns the resulting entity
 *
 * @author kostantinos.kougios
 *
 * 22 Nov 2012
 */
class CmdToDatabase(driver: Driver) {
	private val jdbc = driver.jdbc

	private case class SqlCmd[ID, PC <: DeclaredIds[ID], T](
		cmd: PersistCmd[ID, PC, T],
		sql: driver.sqlBuilder.Result)

	def insert[ID, PC <: DeclaredIds[ID], T](
		cmds: List[PersistCmd[ID, PC, T]]): List[T with PC] = {
		val sqlCmds = cmds.map { cmd =>
			val sql = toSql(cmd)
			SqlCmd(cmd, sql)
		}

		sqlCmds.groupBy(_.sql.sql).map {
			case (sql, cmds) =>
				val bo = BatchOptions(driver.batchStrategy, Array())
				jdbc.batchUpdate(bo, sql, args)
		}

		Nil
	}

	private def toSql(cmd: PersistCmd[_, _, _]) = cmd match {
		case InsertCmd(entity, o, columns) =>
			driver.insertSql(entity.tpe, columns).result
	}
}