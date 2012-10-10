package com.googlecode.mapperdao
import com.googlecode.mapperdao.exceptions.QueryException
import com.googlecode.mapperdao.drivers.Driver
import java.util.concurrent.ConcurrentHashMap
import com.googlecode.mapperdao.sqlbuilder.SqlBuilder
import com.googlecode.mapperdao.utils.NYI

/**
 * the QueryDao implementation
 *
 * runs queries against the database
 *
 * @author kostantinos.kougios
 *
 * 18 Aug 2011
 */
final class QueryDaoImpl private[mapperdao] (typeRegistry: TypeRegistry, driver: Driver, mapperDao: MapperDaoImpl) extends QueryDao {

	import QueryDao._

	def query[ID, PC <: DeclaredIds[ID], T](queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T]): List[T with PC] =
		{
			if (qe == null) throw new NullPointerException("qe can't be null")
			val r = sqlAndArgs(queryConfig, qe).result
			queryInner(queryConfig, qe.entity, r.sql, r.values)
		}

	def lowLevelQuery[ID, PC <: DeclaredIds[ID], T](queryConfig: QueryConfig, entity: Entity[ID, PC, T], sql: String, args: List[Any]): List[T with PC] =
		queryInner(queryConfig, entity, sql, args)

	private def queryInner[ID, PC <: DeclaredIds[ID], T](queryConfig: QueryConfig, entity: Entity[ID, PC, T], sql: String, args: List[Any]) = {
		try {
			val lm = driver.queryForList(queryConfig, entity.tpe, sql, args)

			queryConfig.multi.runStrategy.run(mapperDao, entity, queryConfig, lm)
		} catch {
			case e =>
				val extra = "\n------\nThe query:%s\nThe arguments:%s\n------\n".format(sql, args)
				val msg = "An error occured during execution of query %s.\nQuery Information:%s\nIssue:\n%s".format(sql, extra, e.getMessage)
				throw new QueryException(msg, e)
		}
	}

	def count[ID, PC <: DeclaredIds[ID], T](queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T]): Long =
		{
			if (qe == null) throw new NullPointerException("qe can't be null")
			val aliases = new Aliases(typeRegistry)
			val e = qe.entity
			val tpe = e.tpe
			val q = new driver.sqlBuilder.SqlSelectBuilder
			countSql(q, aliases, e)
			joins(q, defaultQueryConfig, qe, aliases)
			whereAndArgs(q, defaultQueryConfig, qe, aliases)
			val r = q.result
			driver.queryForLong(queryConfig, r.sql, r.values)
		}

	private def sqlAndArgs[ID, PC <: DeclaredIds[ID], T](queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T]) =
		{
			val e = qe.entity
			val tpe = e.tpe
			val columns = tpe.table.selectColumns

			val aliases = new Aliases(typeRegistry)

			val q = new driver.sqlBuilder.SqlSelectBuilder
			val outer = driver.beforeStartOfQuery(q, queryConfig, qe, columns)
			driver.startQuery(q, queryConfig, aliases, qe, columns)
			joins(q, queryConfig, qe, aliases)
			whereAndArgs(q, queryConfig, qe, aliases)
			orderBy(q, queryConfig, qe, aliases)
			driver.endOfQuery(outer, queryConfig, qe)
			outer
		}

	private def joins[ID, PC <: DeclaredIds[ID], T](q: driver.sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T], aliases: Aliases) =
		{
			// iterate through the joins in the correct order
			qe.joins.reverse.foreach {
				case Query.Join(joinEntity, ci, foreignEntity) =>
					val column = ci.column
					column match {
						case manyToOne: ManyToOne[_, _, _] =>
							val join = manyToOneJoin(aliases, joinEntity, foreignEntity, manyToOne)
							q.innerJoin(join)
						case oneToMany: OneToMany[_, _, _] =>
							val join = oneToManyJoin(aliases, joinEntity, foreignEntity, oneToMany)
							q.innerJoin(join)
						case manyToMany: ManyToMany[_, _, _] =>
							val List(leftJoin, rightJoin) = manyToManyJoin(aliases, joinEntity, foreignEntity, manyToMany)
							q.innerJoin(leftJoin)
							q.innerJoin(rightJoin)
						case oneToOneReverse: OneToOneReverse[_, _, _] =>
							val join = oneToOneReverseJoin(aliases, joinEntity, foreignEntity, oneToOneReverse)
							q.innerJoin(join)
						case oneToOne: OneToOne[_, _, _] =>
							val join = oneToOneJoin(aliases, joinEntity, foreignEntity, oneToOne)
							q.innerJoin(join)
					}
				case j: Query.SJoin[Any, DeclaredIds[Any], Any, Any, DeclaredIds[Any], Any, Any, DeclaredIds[Any], Any] =>
					val joined = joinTable(aliases, j)
					q.innerJoin(joined)
			}

			def joins(op: OpBase): Unit = op match {
				case and: AndOp =>
					joins(and.left)
					joins(and.right)
				case and: OrOp =>
					joins(and.left)
					joins(and.right)
				case OneToManyOperation(left: OneToMany[Any, DeclaredIds[Any], _], operand: Operand, right: Any) =>
					val entity = typeRegistry.entityOf(left)
					val foreignEntity = left.foreign.entity
					q.innerJoin(oneToManyJoin(aliases, entity, foreignEntity, left))

				case ManyToManyOperation(left: ManyToMany[Any, DeclaredIds[Any], _], operand: Operand, right: Any) =>
					val foreignEntity = left.foreign.entity
					val entity = typeRegistry.entityOf(left)
					val List(leftJ, rightJ) = manyToManyJoin(aliases, entity, foreignEntity, left)
					q.innerJoin(leftJ)
				case _ => //noop
			}
			// also where clauses might imply joins
			qe.wheres.map(_.clauses).map { op =>
				joins(op)
			}
		}

	private def whereAndArgs[ID, PC <: DeclaredIds[ID], T](q: driver.sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T], aliases: Aliases) =
		// append the where clause and get the list of arguments
		if (!qe.wheres.isEmpty) {
			val e = queryExpressions(aliases, qe.wheres)
			q.where(e)
		}

	private def orderBy[ID, PC <: DeclaredIds[ID], T](q: driver.sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T], aliases: Aliases) =
		if (!qe.order.isEmpty) {
			val orderColumns = qe.order.map { case (ci, ascDesc) => (ci.column, ascDesc) }
			if (driver.shouldCreateOrderByClause(queryConfig)) {
				val obb = new driver.sqlBuilder.OrderByBuilder(
					orderColumns.map {
						case (c, ad) =>
							driver.sqlBuilder.OrderByExpression(c.name, ad.sql)
					}
				)
				q.orderBy(obb)
			}
		}

	private def joinTable[JID, JPC <: DeclaredIds[JID], JT, FID, FPC <: DeclaredIds[FID], FT, QID, QPC <: DeclaredIds[QID], QT](
		aliases: QueryDao.Aliases,
		join: Query.SJoin[JID, JPC, JT, FID, FPC, FT, QID, QPC, QT]) =
		{
			val jEntity = join.entity
			val jTable = jEntity.tpe.table
			val qAlias = aliases(jEntity)

			val e = queryExpressions(aliases, join.on.ons)
			val j = new driver.sqlBuilder.InnerJoinBuilder(jTable.name, qAlias, null)
			j(e)
			j
		}

	// creates the sql and params for expressions (i.e. id=5 and name='x')
	private def queryExpressions[ID, PC <: DeclaredIds[ID], T](aliases: QueryDao.Aliases, wheres: List[Query.Where[ID, PC, T]]): driver.sqlBuilder.Expression =
		{
			def inner(op: OpBase): driver.sqlBuilder.Expression = op match {
				case o: Operation[_] =>
					o.right match {
						case rc: SimpleColumn =>
							driver.sqlBuilder.NonValueClause(aliases(o.left), o.left.name, o.operand.sql, aliases(rc), rc.name)
						case _ =>
							driver.sqlBuilder.Clause(aliases(o.left), o.left, o.operand.sql, o.right)
					}
				case AndOp(left, right) =>
					driver.sqlBuilder.And(inner(left), inner(right))
				case OrOp(left, right) =>
					driver.sqlBuilder.Or(inner(left), inner(right))
				case ManyToOneOperation(left, operand, right) =>
					val exprs = if (right == null) {
						left.columns map { c =>
							val r = operand match {
								case EQ() => "null"
								case NE() => "not null"
								case _ => throw new IllegalArgumentException("operand %s not valid when right hand parameter is null.".format(operand))
							}
							driver.sqlBuilder.NonValueClause(aliases(c), c.name, "is", null, r)
						}
					} else {
						val fTpe = left.foreign.entity.tpe

						val fPKs =
							fTpe.table.toListOfPrimaryKeyValues(right) ::: fTpe.table.toListOfUnusedPrimaryKeySimpleColumnAndValueTuples(right)

						if (left.columns.size != fPKs.size) throw new IllegalStateException("foreign keys %s don't match foreign key columns %s".format(fPKs, left.columns))
						left.columns zip fPKs map {
							case (c, v) =>
								driver.sqlBuilder.Clause(aliases(c), c, operand.sql, v)
						}
					}
					exprs.reduceLeft { (l, r) =>
						driver.sqlBuilder.And(l, r)
					}
				case OneToManyOperation(left: OneToMany[_, _, _], operand: Operand, right: Any) =>
					val foreignEntity = left.foreign.entity
					val fTpe = foreignEntity.tpe
					val fPKColumnAndValues =
						fTpe.table.toListOfPrimaryKeyAndValueTuples(right) ::: fTpe.table.toListOfUnusedPrimaryKeySimpleColumnAndValueTuples(right)
					if (fPKColumnAndValues.isEmpty) throw new IllegalStateException("can't match against an entity that doesn't have a key : %s".format(foreignEntity.clz))
					val exprs = fPKColumnAndValues.map {
						case (c, v) =>
							driver.sqlBuilder.Clause(aliases(c), c, operand.sql, v)
					}
					exprs.reduceLeft[driver.sqlBuilder.Expression] { (l, r) =>
						driver.sqlBuilder.And(l, r)
					}
				case ManyToManyOperation(left: ManyToMany[_, _, _], operand: Operand, right: Any) =>
					val foreignEntity = left.foreign.entity
					val fTpe = foreignEntity.tpe

					val fPKColumnAndValues =
						fTpe.table.toListOfPrimaryKeyAndValueTuples(right) ::: fTpe.table.toListOfUnusedPrimaryKeySimpleColumnAndValueTuples(right)

					if (fPKColumnAndValues.isEmpty) throw new IllegalStateException("can't match against an entity that doesn't have a key : %s".format(foreignEntity.clz))
					if (fPKColumnAndValues.size != left.linkTable.right.size) throw new IllegalStateException("linktable not having the correct right columns for %s and %s".format(fPKColumnAndValues, left.linkTable.right))
					val zipped = (fPKColumnAndValues zip left.linkTable.right)
					zipped.map {
						case ((c, v), ltr) =>
							driver.sqlBuilder.Clause(aliases(left.linkTable), ltr, operand.sql, v)
					}.reduceLeft[driver.sqlBuilder.Expression] { (l, r) =>
						driver.sqlBuilder.And(l, r)
					}
				case OneToOneOperation(left, operand, right) =>
					val foreignEntity = left.foreign.entity
					val fTpe = foreignEntity.tpe
					val fPKColumnAndValues =
						fTpe.table.toListOfPrimaryKeyAndValueTuples(right) ::: fTpe.table.toListOfUnusedPrimaryKeySimpleColumnAndValueTuples(right)

					if (fPKColumnAndValues.isEmpty) throw new IllegalStateException("can't match against an entity that doesn't have a key : %s".format(foreignEntity.clz))
					val exprs = fPKColumnAndValues.map {
						case (c, v) =>
							driver.sqlBuilder.Clause(aliases(left.foreign.entity), c, operand.sql, v)
					}
					exprs.reduceLeft[driver.sqlBuilder.Expression] { (l, r) =>
						driver.sqlBuilder.And(l, r)
					}
				case OneToOneReverseOperation(left, operand, right) =>
					val foreignEntity = left.foreign.entity
					val fTpe = foreignEntity.tpe
					val fPKColumnAndValues =
						fTpe.table.toListOfPrimaryKeyAndValueTuples(right) ::: fTpe.table.toListOfUnusedPrimaryKeySimpleColumnAndValueTuples(right)

					if (fPKColumnAndValues.isEmpty) throw new IllegalStateException("can't match against an entity that doesn't have a key : %s".format(foreignEntity.clz))
					val exprs = fPKColumnAndValues.map {
						case (c, v) =>
							driver.sqlBuilder.Clause(aliases(left.foreign.entity), c, operand.sql, v)
					}
					exprs.reduceLeft[driver.sqlBuilder.Expression] { (l, r) =>
						driver.sqlBuilder.And(l, r)
					}
				case OneToManyDeclaredPrimaryKeyOperation(left, operand, right, foreignEntity) =>
					val fTpe = foreignEntity.tpe
					val fPKColumnAndValues = left.columns zip fTpe.table.toListOfPrimaryKeyValues(right)
					if (fPKColumnAndValues.isEmpty) throw new IllegalStateException("can't match against an entity that doesn't have a key : %s".format(foreignEntity.clz))
					val exprs = fPKColumnAndValues.map {
						case (c, v) =>
							driver.sqlBuilder.Clause(aliases(c), c, operand.sql, v)
					}
					exprs.reduceLeft[driver.sqlBuilder.Expression] { (l, r) =>
						driver.sqlBuilder.And(l, r)
					}
				case SqlFunctionOp(left, operand, right) =>
					driver.sqlBuilder.FunctionClause(aliases, left, Some(operand.sql), right)
				case SqlFunctionBoolOp(left) =>
					new driver.sqlBuilder.FunctionClause(aliases, left)
			}

			wheres.map(_.clauses).map { op =>
				inner(op)
			}.reduceLeft { (l, r) =>
				driver.sqlBuilder.And(l, r)
			}
		}

	// creates the join for one-to-one-reverse
	private def oneToOneReverseJoin[JID, JPC <: DeclaredIds[JID], JT, FID, FPC <: DeclaredIds[FID], FT](
		aliases: QueryDao.Aliases,
		joinEntity: Entity[JID, JPC, JT],
		foreignEntity: Entity[FID, FPC, FT],
		oneToOneReverse: OneToOneReverse[_, _, _]) =
		{
			val tpe = joinEntity.tpe
			val table = tpe.table
			val foreignTpe = foreignEntity.tpe
			val foreignTable = foreignTpe.table
			val fAlias = aliases(foreignEntity)
			val jAlias = aliases(joinEntity)

			val j = new driver.sqlBuilder.InnerJoinBuilder(foreignTable.name, fAlias, null)
			(table.primaryKeys zip oneToOneReverse.foreignColumns).foreach {
				case (left, right) =>
					j.and(jAlias, left.name, "=", fAlias, right.name)
			}
			j
		}

	private def oneToOneJoin[JID, JPC <: DeclaredIds[JID], JT, FID, FPC <: DeclaredIds[FID], FT](
		aliases: QueryDao.Aliases,
		joinEntity: Entity[JID, JPC, JT],
		foreignEntity: Entity[FID, FPC, FT],
		oneToOne: OneToOne[_, _, _]) =
		{
			val tpe = joinEntity.tpe
			val table = tpe.table
			val foreignTpe = foreignEntity.tpe
			val foreignTable = foreignTpe.table
			val fAlias = aliases(foreignEntity)
			val jAlias = aliases(joinEntity)

			val j = new driver.sqlBuilder.InnerJoinBuilder(foreignTable.name, fAlias, null)
			(oneToOne.selfColumns zip foreignTable.primaryKeys) foreach {
				case (left, right) =>
					j.and(jAlias, left.name, "=", fAlias, right.name)
			}
			j
		}

	// creates the join for many-to-one
	private def manyToOneJoin[JID, JPC <: DeclaredIds[JID], JT, FID, FPC <: DeclaredIds[FID], FT](
		aliases: QueryDao.Aliases,
		joinEntity: Entity[JID, JPC, JT],
		foreignEntity: Entity[FID, FPC, FT],
		manyToOne: ManyToOne[_, _, _]) =
		{
			val foreignTable = foreignEntity.tpe.table
			val fAlias = aliases(foreignEntity)
			val jAlias = aliases(joinEntity)

			val j = new driver.sqlBuilder.InnerJoinBuilder(foreignTable.name, fAlias, null)
			(manyToOne.columns zip foreignTable.primaryKeys).foreach {
				case (left, right) =>
					j.and(jAlias, left.name, "=", fAlias, right.name)
			}
			j
		}

	// creates the join for one-to-many
	private def oneToManyJoin[JID, JPC <: DeclaredIds[JID], JT, FID, FPC <: DeclaredIds[FID], FT](
		aliases: QueryDao.Aliases,
		joinEntity: Entity[JID, JPC, JT],
		foreignEntity: Entity[FID, FPC, FT],
		oneToMany: OneToMany[_, _, _]) =
		{
			val joinTpe = joinEntity.tpe
			val foreignTpe = foreignEntity.tpe

			val fAlias = aliases(foreignEntity)
			val jAlias = aliases(joinEntity)

			val j = new driver.sqlBuilder.InnerJoinBuilder(foreignTpe.table.name, fAlias, null)
			(joinTpe.table.primaryKeys zip oneToMany.foreignColumns).foreach {
				case (left, right) =>
					j.and(jAlias, left.name, "=", fAlias, right.name)
			}
			j
		}

	// creates the join for one-to-many
	private def manyToManyJoin[JID, JPC <: DeclaredIds[JID], JT, FID, FPC <: DeclaredIds[FID], FT](
		aliases: QueryDao.Aliases,
		joinEntity: Entity[JID, JPC, JT],
		foreignEntity: Entity[FID, FPC, FT],
		manyToMany: ManyToMany[_, _, _]) =
		{
			val joinTpe = joinEntity.tpe
			val foreignTpe = foreignEntity.tpe

			val foreignTable = foreignTpe.table
			val fAlias = aliases(foreignEntity)
			val jAlias = aliases(joinEntity)

			val linkTable = manyToMany.linkTable
			val linkTableAlias = aliases(linkTable)

			val j1 = new driver.sqlBuilder.InnerJoinBuilder(linkTable.name, linkTableAlias, null)
			(joinTpe.table.primaryKeys zip linkTable.left).foreach {
				case (left, right) =>
					j1.and(linkTableAlias, right.name, "=", jAlias, left.name)
			}

			val j2 = new driver.sqlBuilder.InnerJoinBuilder(foreignTable.name, fAlias, null)
			(foreignTable.primaryKeys zip linkTable.right).foreach {
				case (left, right) =>
					j2.and(fAlias, left.name, "=", linkTableAlias, right.name)
			}
			List(j1, j2)
		}

	/**
	 * =====================================================================================
	 * aggregate methods
	 * =====================================================================================
	 */
	private def countSql[ID, PC <: DeclaredIds[ID], T](q: driver.sqlBuilder.SqlSelectBuilder, aliases: QueryDao.Aliases, entity: Entity[ID, PC, T]): Unit =
		{
			val table = entity.tpe.table
			val alias = aliases(entity)
			q.columnNames(null, List("count(*)"))
			q.from(table.name, alias, null)
		}
}
