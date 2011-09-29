package com.googlecode.mapperdao
import com.googlecode.mapperdao.exceptions.QueryException

/**
 * runs the queries against the database
 *
 * @author kostantinos.kougios
 *
 * 18 Aug 2011
 */
class QueryDao(mapperDao: MapperDao) {

	import QueryDao._

	private val driver = mapperDao.driver
	private val typeRegistry = mapperDao.typeRegistry
	private val jdbc = driver.jdbc

	private class SqlAndArgs(val sql: String, val args: List[Any])

	def query[PC, T](qe: Query.QueryExpressions[PC, T]): List[T with PC] = query(qe.queryEntity)
	def query[PC, T](queryConfig: QueryConfig, qe: Query.QueryExpressions[PC, T]): List[T with PC] = query(queryConfig, qe.queryEntity)

	val defaultQueryConfig = QueryConfig()
	def query[PC, T](qe: Query.QueryEntity[PC, T]): List[T with PC] = query(defaultQueryConfig, qe)
	def query[PC, T](queryConfig: QueryConfig, qe: Query.QueryEntity[PC, T]): List[T with PC] =
		{
			if (qe == null) throw new NullPointerException("qe can't be null")
			var sa: SqlAndArgs = null
			try {
				sa = sqlAndArgs(queryConfig, qe)
				val lm = jdbc.queryForList(sa.sql, sa.args)
				val entityMap = new EntityMap
				val selectConfig = SelectConfig(skip = queryConfig.skip)
				val v = mapperDao.toEntities(lm, typeRegistry.typeOf(qe.entity), selectConfig, entityMap)
				entityMap.done
				v
			} catch {
				case e =>
					val extra = if (sa != null) "\n------\nThe query:%s\nThe arguments:%s\n------\n".format(sa.sql, sa.args) else "None"
					val msg = "An error occured during execution of query %s.\nQuery Information:%s\nIssue:\n%s".format(qe, extra, e.getMessage)
					throw new QueryException(msg, e)
			}
		}

	private def sqlAndArgs[PC, T](queryConfig: QueryConfig, qe: Query.QueryEntity[PC, T]): SqlAndArgs = {
		val e = qe.entity
		val tpe = typeRegistry.typeOf(e)
		val columns = driver.selectColumns(tpe)

		val aliases = new Aliases(typeRegistry)

		val sb = new StringBuilder(driver.startQuery(aliases, qe, columns))
		val joinsSb = new StringBuilder
		val whereSb = new StringBuilder

		var args = List[Any]()
		// iterate through the joins in the correct order
		qe.joins.reverse.foreach { j =>
			val column = j.column
			if (column != null) {
				var foreignEntity = j.foreignEntity
				val joinEntity = j.joinEntity
				j match {
					case join: Query.Join[_, _, _, PC, T] =>
						join.column match {
							case manyToOne: ManyToOne[_] =>
								joinsSb append driver.manyToOneJoin(aliases, joinEntity, foreignEntity, manyToOne)
							case oneToMany: OneToMany[_] =>
								joinsSb append driver.oneToManyJoin(aliases, joinEntity, foreignEntity, oneToMany)
							case manyToMany: ManyToMany[_] =>
								joinsSb append driver.manyToManyJoin(aliases, joinEntity, foreignEntity, manyToMany)
							case oneToOneReverse: OneToOneReverse[_] =>
								joinsSb append driver.oneToOneReverseJoin(aliases, joinEntity, foreignEntity, oneToOneReverse)
						}
				}
			} else {
				val joined = driver.joinTable(aliases, j)
				joinsSb append joined._1
				args = args ::: joined._2
			}
		}

		// append the where clause and get the list of arguments
		if (!qe.wheres.isEmpty) {
			val (sql, wargs) = driver.queryExpressions(aliases, qe.wheres, joinsSb)
			args = args ::: wargs
			whereSb append "\nwhere " append sql
		}

		sb append joinsSb append whereSb
		if (!qe.order.isEmpty) {
			val orderColumns = qe.order.map(t => (t._1.column, t._2))

			val orderBySql = driver.orderBy(aliases, orderColumns)
			sb append "\norder by " append orderBySql
		}
		driver.endOfQuery(queryConfig, qe, sb)
		new SqlAndArgs(sb.toString, args)
	}
}

object QueryDao {

	// creates aliases for tables
	class Aliases(typeRegistry: TypeRegistry) {
		private val aliases = new java.util.IdentityHashMap[Any, String]
		private var aliasCount = new scala.collection.mutable.HashMap[String, Int]

		private def getCnt(prefix: String): Int = {
			val v = aliasCount.getOrElseUpdate(prefix, 1)
			aliasCount(prefix) = v + 1
			v
		}

		def apply[PC, T](entity: Entity[PC, T]): String =
			{
				val v = aliases.get(entity)
				if (v != null) v else {
					val prefix = entity.table.substring(0, 2)

					val v = prefix.toLowerCase + getCnt(prefix)
					aliases.put(entity, v)
					entity.columns.foreach { ci =>
						aliases.put(ci.column, v)
						ci match {
							case ColumnInfoManyToOne(column: ManyToOne[_], _) =>
								column.columns.foreach { c =>
									aliases.put(c, v)
								}
							//							case ColumnInfoTraversableOneToMany(column: OneToMany[_], _) =>
							//								val fTpe=typeRegistry.typeOf(column.foreign.clz)

							case _ =>
						}
					}
					entity.persistedColumns.foreach { ci =>
						aliases.put(ci.column, v)
					}
					v
				}
			}

		def apply(linkTable: LinkTable): String =
			{
				val v = aliases.get(linkTable)
				if (v != null) v else {
					val prefix = linkTable.name.substring(0, 3)

					val v = prefix.toLowerCase + getCnt(prefix)
					aliases.put(linkTable, v)
					v
				}
			}

		def apply(c: ColumnBase): String =
			{
				val v = aliases.get(c)
				if (v == null)
					throw new IllegalStateException("key not found:" + c + " , are your aliases correct?")
				v
			}
	}
}