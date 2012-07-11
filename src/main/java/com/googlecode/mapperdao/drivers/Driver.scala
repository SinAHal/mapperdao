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

	protected[mapperdao] def commaSeparatedListOfSimpleTypeColumns[T](separator: String, columns: Traversable[SimpleColumn], prefix: String = ""): String =
		columns.map(_.name).map(prefix + escapeNamesStrategy.escapeColumnNames(_)).mkString(separator)
	protected[mapperdao] def commaSeparatedListOfSimpleTypeColumns[T](prefix: String, separator: String, columns: List[SimpleColumn]): String =
		columns.map(_.name).map(escapeNamesStrategy.escapeColumnNames _).mkString(prefix, separator + prefix, "")

	protected[mapperdao] def generateColumnsEqualsValueString(l: List[SimpleColumn]): String = generateColumnsEqualsValueString(l, ",\n")

	protected[mapperdao] def generateColumnsEqualsValueString(l: List[SimpleColumn], separator: String): String =
		{
			val sb = new StringBuilder(20)
			var cnt = 0
			l.foreach { ci =>
				if (cnt > 0) sb.append(separator) else cnt += 1
				sb append escapeNamesStrategy.escapeColumnNames(ci.name) append "=?"
			}
			sb.toString
		}
	protected[mapperdao] def generateColumnsEqualsValueString(prefix: String, separator: String, l: List[SimpleColumn]): String =
		{
			val sb = new StringBuilder(20)
			var cnt = 0
			l.foreach { ci =>
				if (cnt > 0) sb.append(separator) else cnt += 1
				sb append prefix append escapeNamesStrategy.escapeColumnNames(ci.name) append "=?"
			}
			sb.toString
		}

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
	def doInsert[PC, T](tpe: Type[PC, T], args: List[(SimpleColumn, Any)]): UpdateResultWithGeneratedKeys =
		{
			val sql = insertSql(tpe, args)
			val a = args.map(_._2)

			val agColumns = tpe.table.autoGeneratedColumns.map(_.name).toArray
			if (agColumns.isEmpty) {
				val ur = jdbc.update(sql, a)
				new UpdateResultWithGeneratedKeys(ur.rowsAffected, Map())
			} else {
				jdbc.updateGetAutoGenerated(sql, agColumns, a)
			}
		}

	protected def sequenceSelectNextSql(sequenceColumn: ColumnBase): String = throw new IllegalStateException("Please implement")
	/**
	 * default impl of the insert statement generation
	 */
	protected def insertSql[PC, T](tpe: Type[PC, T], args: List[(SimpleColumn, Any)]): String =
		{
			val sb = new StringBuilder(100, "insert into ")
			sb append escapeNamesStrategy.escapeTableNames(tpe.table.name)

			val sequenceColumns = tpe.table.simpleTypeSequenceColumns
			if (!args.isEmpty || !sequenceColumns.isEmpty) {
				sb append "("
				// append sequences
				// and normal columns
				if (!args.isEmpty || !sequenceColumns.isEmpty) sb append commaSeparatedListOfSimpleTypeColumns(",", sequenceColumns ::: args.map(_._1))
				sb append ")\n"
				sb append "values("
				// sequence values
				if (!sequenceColumns.isEmpty) {
					sb append sequenceColumns.map { sequenceSelectNextSql _ }.mkString(",")
					if (!args.isEmpty) sb append ","
				}
				// column values
				if (!args.isEmpty) sb append "?" append (",?" * (args.size - 1))
				sb append ")"
			}
			sb.toString
		}

	def doInsertManyToMany[PC, T, FPC, F](
		tpe: Type[PC, T],
		manyToMany: ManyToMany[FPC, F],
		left: List[Any],
		right: List[Any]): Unit =
		{
			val sql = insertManyToManySql(tpe, manyToMany)
			jdbc.update(sql, left ::: right)
		}

	protected def insertManyToManySql[PC, T, FPC, F](tpe: Type[PC, T], manyToMany: ManyToMany[FPC, F]): String =
		{
			val sb = new StringBuilder(100, "insert into ")
			val linkTable = manyToMany.linkTable
			sb append escapeNamesStrategy.escapeTableNames(linkTable.name) append "(" append commaSeparatedListOfSimpleTypeColumns(",", linkTable.left)
			sb append "," append commaSeparatedListOfSimpleTypeColumns(",", linkTable.right) append ")\n"
			sb append "values(?" append (",?" * (linkTable.left.size - 1 + linkTable.right.size)) append ")"
			sb.toString
		}
	/**
	 * =====================================================================================
	 * UPDATE
	 * =====================================================================================
	 */
	/**
	 * default implementation of update, should do for most subclasses
	 */
	def doUpdate[PC, T](tpe: Type[PC, T], args: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]): UpdateResult =
		{
			val sql = updateSql(tpe, args, pkArgs)
			jdbc.update(sql, args.map(_._2) ::: pkArgs.map(_._2))
		}
	/**
	 * default impl of the insert statement generation
	 */
	protected def updateSql[PC, T](tpe: Type[PC, T], args: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]): String =
		{
			val sb = new StringBuilder(100, "update ")
			sb append escapeNamesStrategy.escapeTableNames(tpe.table.name) append "\n"
			sb append "set " append generateColumnsEqualsValueString(args.map(_._1))
			sb append "\nwhere " append generateColumnsEqualsValueString(pkArgs.map(_._1), " and ")
			sb.toString
		}

	/**
	 * links one-to-many objects to their parent
	 */
	def doUpdateOneToManyRef[PC, T](tpe: Type[PC, T], foreignKeys: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]): UpdateResult =
		{
			val sql = updateOneToManyRefSql(tpe, foreignKeys, pkArgs)
			jdbc.update(sql, foreignKeys.map(_._2) ::: pkArgs.map(_._2))
		}

	protected def updateOneToManyRefSql[PC, T](tpe: Type[PC, T], foreignKeys: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]): String =
		{
			val sb = new StringBuilder(100, "update ")
			sb append escapeNamesStrategy.escapeTableNames(tpe.table.name) append "\n"
			sb append "set " append generateColumnsEqualsValueString(foreignKeys.map(_._1))
			sb append "\nwhere " append generateColumnsEqualsValueString(pkArgs.map(_._1))
			sb.toString
		}

	/**
	 * delete many-to-many rows from link table
	 */
	def doDeleteManyToManyRef[PC, T, PR, R](tpe: Type[PC, T], ftpe: Type[PR, R], manyToMany: ManyToMany[_, _], leftKeyValues: List[(SimpleColumn, Any)], rightKeyValues: List[(SimpleColumn, Any)]): UpdateResult =
		{
			val sql = deleteManyToManyRefSql(tpe, ftpe, manyToMany, leftKeyValues, rightKeyValues)
			jdbc.update(sql, leftKeyValues.map(_._2) ::: rightKeyValues.map(_._2))
		}
	protected def deleteManyToManyRefSql[PC, T, PR, R](tpe: Type[PC, T], ftpe: Type[PR, R], manyToMany: ManyToMany[_, _], leftKeyValues: List[(SimpleColumn, Any)], rightKeyValues: List[(SimpleColumn, Any)]): String =
		{
			val sb = new StringBuilder(100, "delete from ")
			sb append escapeNamesStrategy.escapeTableNames(manyToMany.linkTable.name) append "\nwhere "
			sb append generateColumnsEqualsValueString("", " and ", leftKeyValues.map(_._1) ::: rightKeyValues.map(_._1))
			sb.toString
		}

	def doDeleteAllManyToManyRef[PC, T](tpe: Type[PC, T], manyToMany: ManyToMany[_, _], fkKeyValues: List[Any]): UpdateResult = {
		val sql = deleteAllManyToManyRef(tpe, manyToMany, fkKeyValues)
		jdbc.update(sql, fkKeyValues)
	}
	protected def deleteAllManyToManyRef[PC, T](tpe: Type[PC, T], manyToMany: ManyToMany[_, _], fkKeyValues: List[Any]): String = {
		val sb = new StringBuilder(50, "delete from ")
		sb append escapeNamesStrategy.escapeTableNames(manyToMany.linkTable.name) append "\nwhere "
		sb append generateColumnsEqualsValueString("", " and ", manyToMany.linkTable.left)
		sb.toString
	}
	/**
	 * =====================================================================================
	 * SELECT
	 * =====================================================================================
	 */
	def selectColumns[PC, T](tpe: Type[PC, T]): List[SimpleColumn] =
		{
			val table = tpe.table
			table.simpleTypeColumns ::: table.manyToOneColumns.map(_.columns).flatten ::: table.oneToOneColumns.map(_.selfColumns).flatten
		}
	/**
	 * default impl of select
	 */
	def doSelect[PC, T](selectConfig: SelectConfig, tpe: Type[PC, T], where: List[(SimpleColumn, Any)]): List[DatabaseValues] =
		{
			val result = selectSql(selectConfig, tpe, where).result

			// 1st step is to get the simple values
			// of this object from the database
			jdbc.queryForList(result.sql, result.values).map(j => typeManager.correctTypes(tpe.table, j))
		}

	protected def selectSql[PC, T](selectConfig: SelectConfig, tpe: Type[PC, T], where: List[(SimpleColumn, Any)]) =
		{
			val sql = new sqlBuilder.SqlSelectBuilder
			sql.columns(null,
				(
					selectColumns(tpe) ::: tpe.table.unusedPrimaryKeyColumns.collect {
						case c: SimpleColumn => c
					}
				).map(_.name).distinct
			)
			sql.from(tpe.table.name, null, applyHints(selectConfig.hints))
			sql.whereAll(null, where.map {
				case (c, v) =>
					(c.name, v)
			}, "=")
			sql
		}

	private def applyHints(hints: SelectHints) = {
		val h = hints.afterTableName
		if (!h.isEmpty) {
			" " + h.map { _.hint }.mkString(" ") + " "
		} else ""
	}

	def doSelectManyToMany[PC, T, FPC, F](selectConfig: SelectConfig, tpe: Type[PC, T], ftpe: Type[FPC, F], manyToMany: ManyToMany[FPC, F], leftKeyValues: List[(SimpleColumn, Any)]): List[DatabaseValues] =
		{
			val r = selectManyToManySql(selectConfig, tpe, ftpe, manyToMany, leftKeyValues).result
			jdbc.queryForList(r.sql, r.values).map(j => typeManager.correctTypes(ftpe.table, j))
		}

	protected def selectManyToManySql[PC, T, FPC, F](selectConfig: SelectConfig, tpe: Type[PC, T], ftpe: Type[FPC, F], manyToMany: ManyToMany[FPC, F], leftKeyValues: List[(SimpleColumn, Any)]) =
		{
			val ftable = ftpe.table
			val linkTable = manyToMany.linkTable

			val sql = new sqlBuilder.SqlSelectBuilder
			val fColumns = selectColumns(ftpe)
			sql.columns("f", fColumns.map { _.name })
			sql.from(ftpe.table.name, "f", applyHints(selectConfig.hints))
			val j = sql.innerJoin(linkTable.name, "l", applyHints(selectConfig.hints))
			ftable.primaryKeys.zip(linkTable.right).foreach { z =>
				val left = z._1
				val right = z._2
				j.and("f", left.name, "=", "l", right.name)
			}
			val wcs = leftKeyValues.map {
				case (c, v) => (c.name, v)
			}
			sql.whereAll("l", wcs, "=")
			sql
		}

	def doSelectManyToManyCustomLoader[PC, T, FPC, F](selectConfig: SelectConfig, tpe: Type[PC, T], ftpe: Type[FPC, F], manyToMany: ManyToMany[FPC, F], leftKeyValues: List[(SimpleColumn, Any)]): List[JdbcMap] =
		{
			val r = selectManyToManyCustomLoaderSql(selectConfig, tpe, ftpe, manyToMany, leftKeyValues).result
			jdbc.queryForList(r.sql, r.values)
		}

	protected def selectManyToManyCustomLoaderSql[PC, T, FPC, F](selectConfig: SelectConfig, tpe: Type[PC, T], ftpe: Type[FPC, F], manyToMany: ManyToMany[FPC, F], leftKeyValues: List[(SimpleColumn, Any)]) =
		{
			val ftable = ftpe.table
			val linkTable = manyToMany.linkTable
			val sql = new sqlBuilder.SqlSelectBuilder
			sql.columns(null, linkTable.right.map(n => n.name))
			sql.from(linkTable.name, null, applyHints(selectConfig.hints))
			sql.whereAll(null, leftKeyValues.map {
				case (c, v) => (c.name, v)
			}, "=")
			sql

		}
	/**
	 * selects all id's of external entities and returns them in a List[List[Any]]
	 */
	def doSelectManyToManyForExternalEntity[PC, T, FPC, F](selectConfig: SelectConfig, tpe: Type[PC, T], ftpe: Type[FPC, F], manyToMany: ManyToMany[FPC, F], leftKeyValues: List[(SimpleColumn, Any)]): List[List[Any]] =
		{
			val r = selectManyToManySqlForExternalEntity(tpe, ftpe, manyToMany, leftKeyValues).result
			val l = jdbc.queryForList(r.sql, r.values)

			val linkTable = manyToMany.linkTable
			val columns = linkTable.right.map(_.name)
			l.map { j =>
				columns.map(c => j(c))
			}
		}

	protected def selectManyToManySqlForExternalEntity[PC, T, FPC, F](tpe: Type[PC, T], ftpe: Type[FPC, F], manyToMany: ManyToMany[FPC, F], leftKeyValues: List[(SimpleColumn, Any)]) =
		{
			val linkTable = manyToMany.linkTable

			val sql = new sqlBuilder.SqlSelectBuilder
			sql.columns(null, linkTable.right.map(n => n.name))
			sql.from(linkTable.name)
			sql.whereAll(null, leftKeyValues.map {
				case (c, v) => (c.name, v)
			}, "=")
			sql
		}
	/**
	 * =====================================================================================
	 * DELETE
	 * =====================================================================================
	 */
	def doDelete[PC, T](tpe: Type[PC, T], whereColumnValues: List[(SimpleColumn, Any)]): Unit =
		{
			val sql = deleteSql(tpe, whereColumnValues)
			jdbc.update(sql, whereColumnValues.map(_._2))
		}

	protected def deleteSql[PC, T](tpe: Type[PC, T], whereColumnValues: List[(SimpleColumn, Any)]): String =
		{
			val sb = new StringBuilder(100, "delete from ")
			sb append escapeNamesStrategy.escapeTableNames(tpe.table.name) append " where " append generateColumnsEqualsValueString(whereColumnValues.map(_._1), " and ")

			sb.toString
		}

	def doDeleteOneToOneReverse[PC, T, FPC, FT](tpe: Type[PC, T], ftpe: Type[FPC, FT], oneToOneReverse: OneToOneReverse[FPC, FT], keyValues: List[Any]): Unit =
		{
			val sql = deleteOneToOneReverseSql(tpe, ftpe, oneToOneReverse)
			jdbc.update(sql, keyValues)
		}

	def deleteOneToOneReverseSql[PC, T, FPC, FT](tpe: Type[PC, T], ftpe: Type[FPC, FT], oneToOneReverse: OneToOneReverse[FPC, FT]): String =
		{
			val sb = new StringBuilder(100, "delete from ")
			sb append escapeNamesStrategy.escapeTableNames(ftpe.table.name) append " where " append generateColumnsEqualsValueString(oneToOneReverse.foreignColumns, " and ")

			sb.toString
		}
	/**
	 * =====================================================================================
	 * QUERIES
	 * =====================================================================================
	 */

	// select ... from 
	def startQuery[PC, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, aliases: QueryDao.Aliases, qe: Query.Builder[PC, T], columns: List[SimpleColumn]): String =
		{
			val entity = qe.entity
			val tpe = entity.tpe
			val sb = new StringBuilder(100, "select ")
			val qAS = queryAfterSelect(queryConfig, aliases, qe, columns)
			if (!qAS.isEmpty) {
				sb.append(qAS).append(',')
			}
			val alias = aliases(entity)
			sb append commaSeparatedListOfSimpleTypeColumns(alias + ".", ",", columns)
			sb append "\nfrom " append escapeNamesStrategy.escapeTableNames(tpe.table.name) append " " append alias

			sb.toString
		}

	def queryAfterSelect[PC, T](queryConfig: QueryConfig, aliases: QueryDao.Aliases, qe: Query.Builder[PC, T], columns: List[SimpleColumn]): String = ""

	// creates the join for one-to-one-reverse
	def oneToOneReverseJoin(aliases: QueryDao.Aliases, joinEntity: Entity[_, _], foreignEntity: Entity[_, _], oneToOneReverse: OneToOneReverse[_, _]) =
		{
			val tpe = joinEntity.tpe
			val table = tpe.table
			val foreignTpe = foreignEntity.tpe
			val foreignTable = foreignTpe.table
			val fAlias = aliases(foreignEntity)
			val jAlias = aliases(joinEntity)

			val j = new sqlBuilder.InnerJoinBuilder(foreignTable.name, fAlias, null)
			(table.primaryKeys zip oneToOneReverse.foreignColumns).foreach {
				case (left, right) =>
					j.and(jAlias, left.name, "=", fAlias, right.name)
			}
			j

			//			val sb = new StringBuilder
			//			sb append "\njoin " append escapeNamesStrategy.escapeTableNames(foreignTable.name) append " " append fAlias append " on "
			//			(table.primaryKeys zip oneToOneReverse.foreignColumns).foreach { t =>
			//				sb append jAlias append "." append t._1.name append " = " append fAlias append "." append t._2.name append " "
			//			}
			//			sb.toString
		}

	// creates the join for many-to-one
	def manyToOneJoin(aliases: QueryDao.Aliases, joinEntity: Entity[_, _], foreignEntity: Entity[_, _], manyToOne: ManyToOne[_, _]) =
		{
			val foreignTpe = foreignEntity.tpe
			val foreignTable = foreignTpe.table
			val fAlias = aliases(foreignEntity)
			val jAlias = aliases(joinEntity)

			val j = new sqlBuilder.InnerJoinBuilder(foreignTable.name, fAlias, null)
			(manyToOne.columns zip foreignTable.primaryKeys).foreach {
				case (left, right) =>
					j.and(jAlias, left.name, "=", fAlias, right.name)
			}
			j

			//			val sb = new StringBuilder
			//			sb append "\njoin " append escapeNamesStrategy.escapeTableNames(foreignTable.name) append " " append fAlias append " on "
			//			(manyToOne.columns zip foreignTable.primaryKeys).foreach { t =>
			//				sb append jAlias append "." append t._1.name append " = " append fAlias append "." append t._2.name append " "
			//			}
			//			sb.toString
		}

	// creates the join for one-to-many
	def oneToManyJoin(aliases: QueryDao.Aliases, joinEntity: Entity[_, _], foreignEntity: Entity[_, _], oneToMany: OneToMany[_, _]) =
		{
			val joinTpe = joinEntity.tpe
			val foreignTpe = foreignEntity.tpe

			val foreignTable = foreignTpe.table
			val fAlias = aliases(foreignEntity)
			val jAlias = aliases(joinEntity)

			val j = new sqlBuilder.InnerJoinBuilder(foreignTable.name, fAlias, null)
			(joinTpe.table.primaryKeys zip oneToMany.foreignColumns).foreach {
				case (left, right) =>
					j.and(jAlias, left.name, "=", fAlias, right.name)
			}
			j

			//			val sb = new StringBuilder
			//			sb append "\njoin " append escapeNamesStrategy.escapeTableNames(foreignTable.name) append " " append fAlias append " on "
			//			(joinTpe.table.primaryKeys zip oneToMany.foreignColumns).foreach { t =>
			//				sb append jAlias append "." append t._1.name append " = " append fAlias append "." append t._2.name append " "
			//			}
			//			sb.toString
		}
	// creates the join for one-to-many
	def manyToManyJoin(aliases: QueryDao.Aliases, joinEntity: Entity[_, _], foreignEntity: Entity[_, _], manyToMany: ManyToMany[_, _]) =
		{
			val joinTpe = joinEntity.tpe
			val foreignTpe = foreignEntity.tpe

			val foreignTable = foreignTpe.table
			val fAlias = aliases(foreignEntity)
			val jAlias = aliases(joinEntity)

			val linkTable = manyToMany.linkTable
			val linkTableAlias = aliases(linkTable)

			val j1 = new sqlBuilder.InnerJoinBuilder(linkTable.name, linkTableAlias, null)
			(joinTpe.table.primaryKeys zip linkTable.left).foreach {
				case (left, right) =>
					j1.and(linkTableAlias, right.name, "=", jAlias, left.name)
			}

			val j2 = new sqlBuilder.InnerJoinBuilder(foreignTable.name, fAlias, null)
			(foreignTable.primaryKeys zip linkTable.right).foreach {
				case (left, right) =>
					j2.and(fAlias, left.name, "=", linkTableAlias, right.name)
			}
			(j1, j2)

			//			val sb = new StringBuilder
			//			// left part
			//			sb append "\njoin " append escapeNamesStrategy.escapeTableNames(linkTable.name) append " " append linkTableAlias append " on "
			//			(joinTpe.table.primaryKeys zip linkTable.left).foreach { t =>
			//				sb append linkTableAlias append "." append t._2.name append " = " append jAlias append "." append t._1.name append " "
			//			}
			//
			//			// right part
			//			sb append "\njoin " append escapeNamesStrategy.escapeTableNames(foreignTable.name) append " " append fAlias append " on "
			//			(foreignTable.primaryKeys zip linkTable.right).foreach { t =>
			//				sb append fAlias append "." append t._1.name append " = " append linkTableAlias append "." append t._2.name append " "
			//			}
			//			sb.toString
		}

	// creates the join sql and params for joins (including join on expressions, i.e. join T on j1.name<>j2.name)
	def joinTable(aliases: QueryDao.Aliases, join: Query.Join[_, _, Entity[_, _], _, _]) =
		{
			val jEntity = join.entity
			val jTable = jEntity.tpe.table
			val qAlias = aliases(jEntity)

			val j = new sqlBuilder.InnerJoinBuilder(jTable.name, qAlias, null)
			val e = queryExpressions(aliases, join.on.ons)
			j(e)
			j

			//			val sb = new StringBuilder
			//			sb append "\njoin " append escapeNamesStrategy.escapeTableNames(jTable.name) append " " append qAlias
			//
			//			if (join.on != null) {
			//				queryExpressions(q, aliases, join.on.ons, sb)
			//			}
		}

	// creates the sql and params for expressions (i.e. id=5 and name='x')
	def queryExpressions[PC, T](aliases: QueryDao.Aliases, wheres: List[Query.Where[PC, T]]): sqlBuilder.Expression =
		{
			def inner(op: OpBase): sqlBuilder.Expression = op match {
				case o: Operation[_] =>
					o.right match {
						case rc: SimpleColumn =>
							sqlBuilder.NonValueClause(aliases(o.left), o.left.name, o.operand.sql, aliases(rc), rc.name)
						case _ =>
							sqlBuilder.Clause(aliases(o.left), o.left.name, o.operand.sql, o.right)
					}
				case and: AndOp =>
					sqlBuilder.And(inner(and.left), inner(and.right))
				case and: OrOp =>
					sqlBuilder.Or(inner(and.left), inner(and.right))
				case mto: ManyToOneOperation[Any, Any, Any] =>
					val ManyToOneOperation(left, operand, right) = mto
					val exprs = if (right == null) {
						left.columns map { c =>
							val r = operand match {
								case EQ() => "null"
								case NE() => "not null"
								case _ => throw new IllegalArgumentException("operand %s not valid when right hand parameter is null.".format(operand))
							}
							sqlBuilder.NonValueClause(aliases(c), c.name, "is", null, r)
						}
					} else {
						val fTpe = left.foreign.entity.tpe
						val fPKs = fTpe.table.toListOfPrimaryKeyValues(right)
						if (left.columns.size != fPKs.size) throw new IllegalStateException("foreign keys %s don't match foreign key columns %s".format(fPKs, left.columns))
						left.columns zip fPKs map {
							case (c, v) =>
								sqlBuilder.Clause(aliases(c), c.name, operand.sql, v)
						}
					}
					exprs.reduceLeft { (l, r) =>
						sqlBuilder.And(l, r)
					}
				case OneToManyOperation(left: OneToMany[_, _], operand: Operand, right: Any) =>
					//					val entity = typeRegistry.entityOf(left)
					val foreignEntity = left.foreign.entity
					//					val mj = oneToManyJoin(aliases, entity, foreignEntity, left)
					//					q.innerJoin(mj)
					val fTpe = foreignEntity.tpe
					val fPKColumnAndValues = fTpe.table.toListOfPrimaryKeyAndValueTuples(right)
					val exprs = fPKColumnAndValues.map {
						case (c, v) =>
							sqlBuilder.Clause(aliases(c), c.name, operand.sql, v)
					}
					exprs.reduceLeft[sqlBuilder.Expression] { (l, r) =>
						sqlBuilder.And(l, r)
					}
				case ManyToManyOperation(left: ManyToMany[_, _], operand: Operand, right: Any) =>
					//					val entity = typeRegistry.entityOf(left)
					val foreignEntity = left.foreign.entity
					//					val (leftJoin, rightJoin) = manyToManyJoin(aliases, entity, foreignEntity, left)
					//					q.innerJoin(leftJoin)
					//					q.innerJoin(rightJoin)

					val fTpe = foreignEntity.tpe
					val fPKColumnAndValues = fTpe.table.toListOfPrimaryKeyAndValueTuples(right)
					val exprs = fPKColumnAndValues.map {
						case (c, v) =>
							sqlBuilder.Clause(aliases(c), c.name, operand.sql, v)
					}
					exprs.reduceLeft[sqlBuilder.Expression] { (l, r) =>
						sqlBuilder.And(l, r)
					}
			}

			wheres.map(_.clauses).map { op =>
				inner(op)
			}.reduceLeft { (l, r) =>
				sqlBuilder.And(l, r)
			}
		}

	//	protected def resolveWhereExpression(aliases: QueryDao.Aliases, args: scala.collection.mutable.Builder[Any, List[Any]], v: Any): SqlBuilder.Expression = v match {
	//		case c: SimpleColumn =>
	//			aliases(c) + "." + escapeNamesStrategy.escapeColumnNames(c.name)
	//		case _ =>
	//			args += v
	//			"?"
	//	}

	// create order by clause
	def orderBy(queryConfig: QueryConfig, aliases: QueryDao.Aliases, columns: List[(SimpleColumn, Query.AscDesc)]): String = if (shouldCreateOrderByClause(queryConfig)) {
		"\norder by " + columns.map {
			case (c, ascDesc) =>
				aliases(c) + "." + escapeNamesStrategy.escapeColumnNames(c.name) + " " + ascDesc.sql
		}.mkString(",")
	} else ""

	def shouldCreateOrderByClause(queryConfig: QueryConfig): Boolean = true

	// called at the start of each query sql generation, sql is empty at this point
	def beforeStartOfQuery[PC, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[PC, T], columns: List[SimpleColumn]): sqlBuilder.SqlSelectBuilder = q
	// called at the end of each query sql generation
	def endOfQuery[PC, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[PC, T]): sqlBuilder.SqlSelectBuilder = q

	/**
	 * =====================================================================================
	 * aggregate methods
	 * =====================================================================================
	 */
	def countSql[PC, T](q: sqlBuilder.SqlSelectBuilder, aliases: QueryDao.Aliases, entity: Entity[PC, T]): Unit =
		{
			val table = entity.tpe.table
			val alias = aliases(entity)
			q.columns(null, List("count(*)"))
			q.from(table.name, alias, null)

			//			val sb = new StringBuilder(50, "select count(*)")
			//			sb append "\nfrom " append escapeNamesStrategy.escapeTableNames(tpe.table.name) append " " append alias
			//			sb.toString
		}

	/**
	 * =====================================================================================
	 * generic queries
	 * =====================================================================================
	 */
	def queryForList[PC, T](queryConfig: QueryConfig, tpe: Type[PC, T], sql: String, args: List[Any]): List[DatabaseValues] =
		jdbc.queryForList(sql, args).map { j => typeManager.correctTypes(tpe.table, j) }
	def queryForLong(queryConfig: QueryConfig, sql: String, args: List[Any]): Long = jdbc.queryForLong(sql, args)
	/**
	 * =====================================================================================
	 * standard methods
	 * =====================================================================================
	 */
	override def toString = "Driver(%s,%s)".format(jdbc, typeRegistry)
}