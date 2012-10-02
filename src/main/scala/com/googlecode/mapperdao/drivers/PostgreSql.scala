package com.googlecode.mapperdao.drivers
import com.googlecode.mapperdao.jdbc.Jdbc
import com.googlecode.mapperdao.TypeRegistry
import com.googlecode.mapperdao.ColumnBase
import com.googlecode.mapperdao.Type
import com.googlecode.mapperdao.PK
import com.googlecode.mapperdao.QueryConfig
import com.googlecode.mapperdao.Query
import com.googlecode.mapperdao.TypeManager
import com.googlecode.mapperdao.SimpleColumn
import com.googlecode.mapperdao.sqlbuilder.SqlBuilder

/**
 * @author kostantinos.kougios
 *
 * 14 Jul 2011
 */
class PostgreSql(val jdbc: Jdbc, val typeRegistry: TypeRegistry, val typeManager: TypeManager) extends Driver {

	val escapeNamesStrategy = new EscapeNamesStrategy {
		val invalidColumnNames = Set("end", "select", "where", "group")
		val invalidTableNames = Set("end", "select", "where", "group", "user")

		override def escapeColumnNames(name: String) = if (invalidColumnNames.contains(name.toLowerCase)) '"' + name + '"'; else name
		override def escapeTableNames(name: String) = if (invalidTableNames.contains(name.toLowerCase)) '"' + name + '"'; else name
	}
	val sqlBuilder = new SqlBuilder(this, escapeNamesStrategy)

	override protected def sequenceSelectNextSql(sequenceColumn: ColumnBase): String = sequenceColumn match {
		case PK(columnName, true, sequence, _) => "NEXTVAL('%s')".format(sequence.get)
	}

	override def endOfQuery[ID, PC, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T]) =
		{
			queryConfig.offset.foreach(o => q.appendSql("offset " + o))
			queryConfig.limit.foreach(l => q.appendSql("limit " + l))
			q
		}

	override def toString = "PostgreSql"
}