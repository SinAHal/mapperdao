package com.googlecode.mapperdao.drivers
import com.googlecode.mapperdao._
import com.googlecode.mapperdao.jdbc.JdbcMap
import com.googlecode.mapperdao.jdbc.UpdateResultWithGeneratedKeys
import com.googlecode.mapperdao.jdbc.Jdbc
import com.googlecode.mapperdao.jdbc.UpdateResult
import com.googlecode.mapperdao.sqlbuilder.SqlBuilder

/**
 * all database drivers must implement this trait
 *
 * @author kostantinos.kougios
 *
 * 14 Jul 2011
 */
abstract class Driver {
	val jdbc: Jdbc
	val typeRegistry: TypeRegistry
	val typeManager: TypeManager

	/**
	 * =====================================================================================
	 * utility methods
	 * =====================================================================================
	 */
	val escapeNamesStrategy: EscapeNamesStrategy
	val sqlBuilder: SqlBuilder

	protected[mapperdao] def getAutoGenerated(ur: UpdateResultWithGeneratedKeys, column: SimpleColumn): Any =
		ur.keys.get(column.name).get

	/**
	 * =====================================================================================
	 * INSERT
	 * =====================================================================================
	 */

	/**
	 * default implementation of insert, should do for most subclasses
	 */
	def doInsert[ID, PC <: DeclaredIds[ID], T](tpe: Type[ID, PC, T], args: List[(SimpleColumn, Any)]): UpdateResultWithGeneratedKeys =
		{
			val r = insertSql(tpe, args).result

			val agColumns = tpe.table.autoGeneratedColumns.map(_.name)
			if (agColumns.isEmpty) {
				val ur = jdbc.update(r.sql, r.values)
				new UpdateResultWithGeneratedKeys(ur.rowsAffected, Map())
			} else {
				jdbc.updateGetAutoGenerated(r.sql, agColumns.toArray, r.values)
			}
		}

	protected def sequenceSelectNextSql(sequenceColumn: ColumnBase): String = throw new IllegalStateException("Please implement")
	/**
	 * default impl of the insert statement generation
	 */
	protected def insertSql[ID, PC <: DeclaredIds[ID], T](tpe: Type[ID, PC, T], args: List[(SimpleColumn, Any)]) =
		{
			val s = new sqlBuilder.InsertBuilder
			s.into(tpe.table.name)

			val sequenceColumns = tpe.table.simpleTypeSequenceColumns
			if (!args.isEmpty || !sequenceColumns.isEmpty) {
				val seqVal = sequenceColumns zip sequenceColumns.map { sequenceSelectNextSql _ }
				s.columnAndSequences(seqVal)
				s.columnAndValues(args)
			}
			s
		}

	def doInsertManyToMany[ID, PC <: DeclaredIds[ID], T, FID, FPC <: DeclaredIds[FID], F](
		tpe: Type[ID, PC, T],
		manyToMany: ManyToMany[FID, FPC, F],
		left: List[Any],
		right: List[Any]): Unit =
		{
			val r = insertManyToManySql(tpe, manyToMany, left ::: right).result
			jdbc.update(r.sql, r.values)
		}

	protected def insertManyToManySql[ID, PC <: DeclaredIds[ID], T, FID, FPC <: DeclaredIds[FID], F](tpe: Type[ID, PC, T], manyToMany: ManyToMany[FID, FPC, F], values: List[Any]) =
		{
			val s = new sqlBuilder.InsertBuilder
			val linkTable = manyToMany.linkTable
			s.into(linkTable.name)
			val cav = (linkTable.left ::: linkTable.right) zip values
			s.columnAndValues(cav)
			s
		}
	/**
	 * =====================================================================================
	 * UPDATE
	 * =====================================================================================
	 */
	/**
	 * default implementation of update, should do for most subclasses
	 */
	def doUpdate[ID, PC <: DeclaredIds[ID], T](tpe: Type[ID, PC, T], args: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]): UpdateResult =
		{
			val r = updateSql(tpe, args, pkArgs).results
			jdbc.update(r.sql, r.values)
		}
	/**
	 * default impl of the insert statement generation
	 */
	protected def updateSql[ID, PC <: DeclaredIds[ID], T](tpe: Type[ID, PC, T], args: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]) =
		{
			val s = new sqlBuilder.UpdateBuilder
			s.table(tpe.table.name)
			s.set(args)
			s.where(pkArgs, "=")
			s
		}

	/**
	 * links one-to-many objects to their parent
	 */
	def doUpdateOneToManyRef[ID, PC <: DeclaredIds[ID], T](tpe: Type[ID, PC, T], foreignKeys: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]): UpdateResult =
		{
			val r = updateOneToManyRefSql(tpe, foreignKeys, pkArgs).results
			jdbc.update(r.sql, r.values)
		}

	protected def updateOneToManyRefSql[ID, PC <: DeclaredIds[ID], T](tpe: Type[ID, PC, T], foreignKeys: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]) =
		{
			val s = new sqlBuilder.UpdateBuilder
			s.table(tpe.table.name)
			s.set(foreignKeys)
			s.where(pkArgs, "=")
			s
		}

	/**
	 * delete many-to-many rows from link table
	 */
	def doDeleteManyToManyRef[ID, PC <: DeclaredIds[ID], T, PID, PR <: DeclaredIds[PID], R](tpe: Type[ID, PC, T], ftpe: Type[PID, PR, R], manyToMany: ManyToMany[_, _, _], leftKeyValues: List[(SimpleColumn, Any)], rightKeyValues: List[(SimpleColumn, Any)]): UpdateResult =
		{
			val r = deleteManyToManyRefSql(tpe, ftpe, manyToMany, leftKeyValues, rightKeyValues).result
			jdbc.update(r.sql, r.values)
		}
	protected def deleteManyToManyRefSql[ID, PC <: DeclaredIds[ID], T, PID, PR <: DeclaredIds[PID], R](tpe: Type[ID, PC, T], ftpe: Type[PID, PR, R], manyToMany: ManyToMany[_, _, _], leftKeyValues: List[(SimpleColumn, Any)], rightKeyValues: List[(SimpleColumn, Any)]) =
		{
			val s = new sqlBuilder.DeleteBuilder
			s.from(manyToMany.linkTable.name)
			s.where(leftKeyValues ::: rightKeyValues, "=")
			s
		}

	def doDeleteAllManyToManyRef[ID, PC <: DeclaredIds[ID], T](tpe: Type[ID, PC, T], manyToMany: ManyToMany[_, _, _], fkKeyValues: List[Any]): UpdateResult = {
		val r = deleteAllManyToManyRef(tpe, manyToMany, fkKeyValues).result
		jdbc.update(r.sql, r.values)
	}
	protected def deleteAllManyToManyRef[ID, PC <: DeclaredIds[ID], T](tpe: Type[ID, PC, T], manyToMany: ManyToMany[_, _, _], fkKeyValues: List[Any]) = {
		val s = new sqlBuilder.DeleteBuilder
		s.from(manyToMany.linkTable.name)
		s.where(manyToMany.linkTable.left zip fkKeyValues, "=")
		s
	}
	/**
	 * =====================================================================================
	 * SELECT
	 * =====================================================================================
	 */

	/**
	 * default impl of select
	 */
	def doSelect[ID, PC <: DeclaredIds[ID], T](selectConfig: SelectConfig, tpe: Type[ID, PC, T], where: List[(SimpleColumn, Any)]): List[DatabaseValues] =
		{
			val result = selectSql(selectConfig, tpe, where).result

			// 1st step is to get the simple values
			// of this object from the database
			jdbc.queryForList(result.sql, result.values).map { j =>
				typeManager.correctTypes(tpe.table, j)
			}
		}

	protected def selectSql[ID, PC <: DeclaredIds[ID], T](selectConfig: SelectConfig, tpe: Type[ID, PC, T], where: List[(SimpleColumn, Any)]) =
		{
			val sql = new sqlBuilder.SqlSelectBuilder
			sql.columns(null,
				tpe.table.distinctSelectColumnsForSelect
			)
			sql.from(tpe.table.name, null, applyHints(selectConfig.hints))
			sql.where(null, where, "=")
			sql
		}

	private def applyHints(hints: SelectHints) = {
		val h = hints.afterTableName
		if (!h.isEmpty) {
			" " + h.map { _.hint }.mkString(" ") + " "
		} else ""
	}

	def doSelectManyToMany[ID, PC <: DeclaredIds[ID], T, FID, FPC <: DeclaredIds[FID], F](selectConfig: SelectConfig, tpe: Type[ID, PC, T], ftpe: Type[FID, FPC, F], manyToMany: ManyToMany[FID, FPC, F], leftKeyValues: List[(SimpleColumn, Any)]): List[DatabaseValues] =
		{
			val r = selectManyToManySql(selectConfig, tpe, ftpe, manyToMany, leftKeyValues).result
			jdbc.queryForList(r.sql, r.values).map(j => typeManager.correctTypes(ftpe.table, j))
		}

	protected def selectManyToManySql[ID, PC <: DeclaredIds[ID], T, FID, FPC <: DeclaredIds[FID], F](selectConfig: SelectConfig, tpe: Type[ID, PC, T], ftpe: Type[FID, FPC, F], manyToMany: ManyToMany[FID, FPC, F], leftKeyValues: List[(SimpleColumn, Any)]) =
		{
			val ftable = ftpe.table
			val linkTable = manyToMany.linkTable

			val sql = new sqlBuilder.SqlSelectBuilder
			val fColumns = ftpe.table.selectColumns
			sql.columns("f", fColumns)
			sql.from(ftpe.table.name, "f", applyHints(selectConfig.hints))
			val j = sql.innerJoin(linkTable.name, "l", applyHints(selectConfig.hints))
			ftable.primaryKeys.zip(linkTable.right).foreach {
				case (left, right) =>
					j.and("f", left.name, "=", "l", right.name)
			}
			sql.where("l", leftKeyValues, "=")
			sql
		}

	def doSelectManyToManyCustomLoader[ID, PC <: DeclaredIds[ID], T, FID, FPC <: DeclaredIds[FID], F](selectConfig: SelectConfig, tpe: Type[ID, PC, T], ftpe: Type[FID, FPC, F], manyToMany: ManyToMany[FID, FPC, F], leftKeyValues: List[(SimpleColumn, Any)]): List[JdbcMap] =
		{
			val r = selectManyToManyCustomLoaderSql(selectConfig, tpe, ftpe, manyToMany, leftKeyValues).result
			jdbc.queryForList(r.sql, r.values)
		}

	protected def selectManyToManyCustomLoaderSql[ID, PC <: DeclaredIds[ID], T, FID, FPC <: DeclaredIds[FID], F](selectConfig: SelectConfig, tpe: Type[ID, PC, T], ftpe: Type[FID, FPC, F], manyToMany: ManyToMany[FID, FPC, F], leftKeyValues: List[(SimpleColumn, Any)]) =
		{
			val ftable = ftpe.table
			val linkTable = manyToMany.linkTable
			val sql = new sqlBuilder.SqlSelectBuilder
			sql.columns(null, linkTable.right)
			sql.from(linkTable.name, null, applyHints(selectConfig.hints))
			sql.where(null, leftKeyValues, "=")
			sql

		}
	/**
	 * selects all id's of external entities and returns them in a List[List[Any]]
	 */
	def doSelectManyToManyForExternalEntity[ID, PC <: DeclaredIds[ID], T, FID, FPC <: DeclaredIds[FID], F](selectConfig: SelectConfig, tpe: Type[ID, PC, T], ftpe: Type[FID, FPC, F], manyToMany: ManyToMany[FID, FPC, F], leftKeyValues: List[(SimpleColumn, Any)]): List[List[Any]] =
		{
			val r = selectManyToManySqlForExternalEntity(tpe, ftpe, manyToMany, leftKeyValues).result
			val l = jdbc.queryForList(r.sql, r.values)

			val linkTable = manyToMany.linkTable
			val columns = linkTable.right.map(_.name)
			l.map { j =>
				columns.map(c => j(c))
			}
		}

	protected def selectManyToManySqlForExternalEntity[ID, PC <: DeclaredIds[ID], T, FID, FPC <: DeclaredIds[FID], F](tpe: Type[ID, PC, T], ftpe: Type[FID, FPC, F], manyToMany: ManyToMany[FID, FPC, F], leftKeyValues: List[(SimpleColumn, Any)]) =
		{
			val linkTable = manyToMany.linkTable

			val sql = new sqlBuilder.SqlSelectBuilder
			sql.columns(null, linkTable.right)
			sql.from(linkTable.name)
			sql.where(null, leftKeyValues, "=")
			sql
		}
	/**
	 * =====================================================================================
	 * DELETE
	 * =====================================================================================
	 */
	def doDelete[ID, PC <: DeclaredIds[ID], T](tpe: Type[ID, PC, T], whereColumnValues: List[(SimpleColumn, Any)]): Unit =
		{
			val s = deleteSql(tpe, whereColumnValues).result
			jdbc.update(s.sql, s.values)
		}

	protected def deleteSql[ID, PC <: DeclaredIds[ID], T](tpe: Type[ID, PC, T], whereColumnValues: List[(SimpleColumn, Any)]) =
		{
			val s = new sqlBuilder.DeleteBuilder
			s.from(sqlBuilder.Table(tpe.table.name))
			s.where(whereColumnValues, "=")
			s
		}

	def doDeleteOneToOneReverse[ID, PC <: DeclaredIds[ID], T, FID, FPC <: DeclaredIds[FID], FT](tpe: Type[ID, PC, T], ftpe: Type[FID, FPC, FT], oneToOneReverse: OneToOneReverse[FID, FPC, FT], keyValues: List[Any]): Unit =
		{
			val r = deleteOneToOneReverseSql(tpe, ftpe, oneToOneReverse.foreignColumns zip keyValues).result
			jdbc.update(r.sql, r.values)
		}

	def deleteOneToOneReverseSql[ID, PC <: DeclaredIds[ID], T, FID, FPC <: DeclaredIds[FID], FT](tpe: Type[ID, PC, T], ftpe: Type[FID, FPC, FT], columnAndValues: List[(SimpleColumn, Any)]) =
		{
			val s = new sqlBuilder.DeleteBuilder
			s.from(sqlBuilder.Table(ftpe.table.name))
			s.where(columnAndValues, "=")
			s
		}
	/**
	 * =====================================================================================
	 * QUERIES
	 * =====================================================================================
	 */

	// select ... from 
	def startQuery[ID, PC <: DeclaredIds[ID], T](
		q: sqlBuilder.SqlSelectBuilder,
		queryConfig: QueryConfig,
		aliases: QueryDao.Aliases,
		qe: Query.Builder[ID, PC, T], columns: List[SimpleColumn]) =
		{
			val entity = qe.entity
			val tpe = entity.tpe
			queryAfterSelect(q, queryConfig, aliases, qe, columns)
			val alias = aliases(entity)

			q.columns(alias, columns)
			q.from(tpe.table.name, alias, null)
		}

	def queryAfterSelect[ID, PC <: DeclaredIds[ID], T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, aliases: QueryDao.Aliases, qe: Query.Builder[ID, PC, T], columns: List[SimpleColumn]): Unit = {}

	def shouldCreateOrderByClause(queryConfig: QueryConfig): Boolean = true

	// called at the start of each query sql generation, sql is empty at this point
	def beforeStartOfQuery[ID, PC <: DeclaredIds[ID], T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T], columns: List[SimpleColumn]): sqlBuilder.SqlSelectBuilder = q
	// called at the end of each query sql generation
	def endOfQuery[ID, PC <: DeclaredIds[ID], T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T]): sqlBuilder.SqlSelectBuilder = q

	/**
	 * =====================================================================================
	 * generic queries
	 * =====================================================================================
	 */
	def queryForList[ID, PC <: DeclaredIds[ID], T](queryConfig: QueryConfig, tpe: Type[ID, PC, T], sql: String, args: List[Any]): List[DatabaseValues] =
		jdbc.queryForList(sql, args).map { j => typeManager.correctTypes(tpe.table, j) }

	def queryForLong(queryConfig: QueryConfig, sql: String, args: List[Any]): Long = jdbc.queryForLong(sql, args)
	/**
	 * =====================================================================================
	 * sql-function related methods
	 * =====================================================================================
	 */

	def functionCallPrependUser: Option[String] = None

	/**
	 * =====================================================================================
	 * standard methods
	 * =====================================================================================
	 */
	override def toString = "Driver(%s,%s)".format(jdbc, typeRegistry)
}