package com.googlecode.mapperdao.drivers

import com.googlecode.mapperdao.jdbc.Jdbc
import com.googlecode.mapperdao.jdbc.UpdateResultWithGeneratedKeys
import com.googlecode.mapperdao.sqlbuilder.SqlBuilder
import com.googlecode.mapperdao._
import com.googlecode.mapperdao.jdbc.Batch

/**
 * mapperdao driver for Sql Server
 *
 * @author kostantinos.kougios
 *
 *         13 Nov 2011
 */
class SqlServer(val jdbc: Jdbc, val typeRegistry: TypeRegistry, val typeManager: TypeManager) extends Driver {

	def batchStrategy(autogenerated: Boolean) = if (autogenerated) Batch.NoBatch else Batch.WithBatch

	val escapeNamesStrategy = new EscapeNamesStrategy {
		val invalidColumnNames = Set("end", "select", "where", "group", "user", "double")
		val invalidTableNames = Set("end", "select", "where", "group", "user")

		override def escapeColumnNames(name: String) = if (invalidColumnNames.contains(name.toLowerCase)) '[' + name + ']'; else name

		override def escapeTableNames(name: String) = if (invalidTableNames.contains(name.toLowerCase)) '[' + name + ']'; else name
	}
	val sqlBuilder = new SqlBuilder(this, escapeNamesStrategy)

	protected[mapperdao] override def getAutoGenerated(
		m: java.util.Map[String, Object],
		column: SimpleColumn
	) =
		m.get("GENERATED_KEYS")

	/**
	 * attempt to create queries like
	 * select * from
	 * (
	 * select
	 * ROW_NUMBER() over (order by companyName desc) as Row,
	 * customerId,companyName
	 * from Customers
	 * ) as t
	 * where Row between 3 and 5
	 */
	override def queryAfterSelect[ID, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, aliases: QueryDao.Aliases, qe: Query.Builder[ID, T], columns: List[SimpleColumn]) =
		if (queryConfig.hasRange) {
			val sb = new StringBuilder("ROW_NUMBER() over (order by ")
			val entity = qe.entity
			val alias = aliases(qe.entity)
			val orderBySql = qe.order.map(t => alias + "." + t._1.column.name + " " + t._2.sql).mkString(",")
			if (orderBySql.isEmpty) {
				sb append entity.tpe.table.primaryKeys.map(alias + "." + _.name).mkString(",")
			} else sb append orderBySql

			sb append ") as Row"
			val sql = sb.toString
			q.columnNames(null, List(sql))
		}

	override def beforeStartOfQuery[ID, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[ID, T], columns: List[SimpleColumn]) =
		if (queryConfig.hasRange) {
			val nq = new sqlBuilder.SqlSelectBuilder
			nq.columnNames(null, List("*"))
			nq.from(q, "t")
			nq
		} else q

	private val row = Column("Row", classOf[Long])

	override def endOfQuery[ID, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[ID, T]) =
		if (queryConfig.hasRange) {
			val offset = queryConfig.offset.getOrElse(0l) + 1
			val w = sqlBuilder.Between(null, row, offset, (if (queryConfig.limit.isDefined) queryConfig.limit.get + offset - 1 else Long.MaxValue))
			q.where(w)
			q
		} else q

	override def shouldCreateOrderByClause(queryConfig: QueryConfig): Boolean = !queryConfig.hasRange

	override val functionCallPrependUser = Some("dbo")

	override def toString = "SqlServer"
}