package com.googlecode.mapperdao.sqlbuilder

import com.googlecode.mapperdao.drivers.EscapeNamesStrategy
import org.springframework.jdbc.core.SqlParameterValue
import com.googlecode.mapperdao.jdbc.Jdbc
import com.googlecode.mapperdao.QueryDao
import com.googlecode.mapperdao.drivers.Driver
import com.googlecode.mapperdao.schema._
import com.googlecode.mapperdao.sqlfunction.SqlFunctionValue
import com.googlecode.mapperdao.schema.ColumnInfo
import scala.Some
import com.googlecode.mapperdao.schema.ColumnInfoManyToOne
import com.googlecode.mapperdao.schema.ColumnInfoOneToOne

/**
 * builds queries, inserts, updates and deletes. This is a thread-safe factory, 1 instance can be reused
 * to create builders.
 *
 * @author kostantinos.kougios
 *
 *         8 Jul 2012
 */

private[mapperdao] class SqlBuilder(driver: Driver, escapeNamesStrategy: EscapeNamesStrategy)
{

	trait Expression
	{
		def toSql: String

		def toValues: List[SqlParameterValue]
	}

	object EmptyExpression extends Expression
	{
		def toSql = ""

		def toValues = Nil
	}

	abstract class Combine extends Expression
	{
		val left: Expression
		val right: Expression
	}

	case class And(left: Expression, right: Expression) extends Combine
	{
		override def toSql = "(" + left.toSql + ") and (" + right.toSql + ")"

		override def toValues = left.toValues ::: right.toValues
	}

	case class Or(left: Expression, right: Expression) extends Combine
	{
		override def toSql = "(" + left.toSql + ") or (" + right.toSql + ")"

		override def toValues = left.toValues ::: right.toValues
	}

	case class Comma(expressions: List[Expression]) extends Expression
	{
		override def toSql = expressions.map(_.toSql).mkString(",")

		override def toValues = expressions.map(_.toValues).flatten
	}

	case class Clause(
		alias: String,
		column: SimpleColumn,
		op: String,
		value: Any
		) extends Expression
	{

		private def isNull = value == null && op == "="

		override def toSql = {
			val sb = new StringBuilder
			if (alias != null) sb append (alias) append (".")
			sb append escapeNamesStrategy.escapeColumnNames(column.name) append " "
			if (isNull)
				sb append "is null"
			else
				sb append op append " ?"
			sb.toString()
		}

		override def toValues = if (isNull) Nil
		else List(
			Jdbc.toSqlParameter(driver, column.tpe, value)
		)
	}

	case class NonValueClause(
		leftAlias: String, left: String,
		op: String,
		rightAlias: String, right: String
		) extends Expression
	{

		override def toSql = {
			val sb = new StringBuilder
			if (leftAlias != null) sb append (leftAlias) append (".")
			sb append escapeNamesStrategy.escapeColumnNames(left) append " " append op append " "
			if (rightAlias != null) sb append rightAlias append "."
			sb append escapeNamesStrategy.escapeColumnNames(right)
			sb.toString()
		}

		override def toValues = Nil
	}

	class ColumnAndColumnClause(
		leftAlias: String, leftColumn: SimpleColumn,
		op: String,
		rightAlias: String, rightColumn: SimpleColumn
		) extends NonValueClause(leftAlias, leftColumn.name, op, rightAlias, rightColumn.name)

	case class FunctionClause[R](
		aliases: QueryDao.Aliases,
		left: SqlFunctionValue[R],
		op: Option[String],
		right: Any
		) extends Expression
	{

		def this(
			aliases: QueryDao.Aliases,
			left: SqlFunctionValue[R]
			) = this(aliases, left, None, null)

		if (op.isDefined && right == null) throw new NullPointerException("right-part of expression can't be null, for " + left)

		private val rightValues = if (op.isDefined)
			right match {
				case null => throw new NullPointerException("null values not allowed as function parameters")
				case v if (Jdbc.isPrimitiveJdbcType(driver, v.getClass)) => List(Jdbc.toSqlParameter(driver, right.getClass, right))
				case f: SqlFunctionValue[_] => functionToValues(f)
				case _ => Nil
			}
		else Nil

		private def functionToValues[T](v: SqlFunctionValue[T]): List[SqlParameterValue] =
			v.values.collect {
				case value if (Jdbc.isPrimitiveJdbcType(driver, value.getClass)) =>
					List(Jdbc.toSqlParameter(driver, value.getClass, value))
				case iv: SqlFunctionValue[_] =>
					functionToValues(iv)
			}.flatten

		private val leftValues = functionToValues(left)

		private def functionCall(v: SqlFunctionValue[_]) = v.schema.map(_.name + ".").getOrElse("") + v.name

		private def functionToSql[T](v: SqlFunctionValue[T]): String = {
			val sb = new StringBuilder(functionCall(v)) append '('
			sb append v.values.map {
				case value if (Jdbc.isPrimitiveJdbcType(driver, value.getClass)) =>
					"?"
				case ci: ColumnInfo[_, _] => aliases(ci.column) + "." + ci.column.name
				case ci: ColumnInfoManyToOne[_, _, _] =>
					ci.column.columns.map {
						c =>
							aliases(ci.column) + "." + c.name
					}.mkString(",")
				case ci: ColumnInfoOneToOne[_, _, _] =>
					ci.column.columns.map {
						c =>
							aliases(ci.column) + "." + c.name
					}.mkString(",")
				case iv: SqlFunctionValue[_] =>
					functionToSql(iv)
			}.mkString(",")
			sb append ')'
			sb.toString()
		}

		override def toSql = {
			val sb = new StringBuilder(functionToSql(left))
			if (op.isDefined) {
				sb append op.get
				sb append (right match {
					case v if (Jdbc.isPrimitiveJdbcType(driver, right.getClass)) => "?"
					case ci: ColumnInfo[_, _] => aliases(ci.column) + "." + ci.column.name
					case ci: ColumnInfoManyToOne[_, _, _] =>
						if (ci.column.columns.size > 1) throw new IllegalArgumentException("can't use a multi-column-primary-key many-to-one in the right part of a function comparison : " + ci.column.columns)
						ci.column.columns.map {
							c =>
								aliases(ci.column) + "." + c.name
						}.mkString(",")
					case ci: ColumnInfoOneToOne[_, _, _] =>
						if (ci.column.columns.size > 1) throw new IllegalArgumentException("can't use a multi-column-primary-key one-to-one in the right part of a function comparison : " + ci.column.columns)
						ci.column.columns.map {
							c =>
								aliases(ci.column) + "." + c.name
						}.mkString(",")
					case right: SqlFunctionValue[_] =>
						functionToSql(right)
				})
			}
			sb.toString()
		}

		override def toValues = leftValues ::: rightValues
	}

	case class Between(alias: String, column: SimpleColumn, left: Any, right: Any) extends Expression
	{
		override def toSql = escapeNamesStrategy.escapeColumnNames(column.name) + " " + (if (alias != null) alias else "") + " between ? and ?"

		override def toValues = Jdbc.toSqlParameter(driver, column.tpe, left) :: Jdbc.toSqlParameter(driver, column.tpe, right) :: Nil
	}

	trait FromClause
	{
		def toSql: String

		def toValues: List[SqlParameterValue]
	}

	case class Table(schema: Option[String], schemaModifications: SchemaModifications, table: String, alias: String = null, hints: String = null) extends FromClause
	{
		private val n = escapeNamesStrategy.escapeTableNames(schemaModifications.tableNameTransformer(table))

		def tableName = if (schema.isDefined) schema.get + "." + n else n

		def toSql = {
			val sb = new StringBuilder

			if (schema.isDefined) sb append schema.get append "."

			sb append n
			if (alias != null) sb append " " append alias
			if (hints != null) sb append " " append hints
			sb.toString
		}

		def toValues = Nil
	}

	class InnerJoinBuilder(table: Table)
	{
		private var e: Expression = null

		def on(leftAlias: String, left: String, op: String, rightAlias: String, right: String) = {
			if (e != null) throw new IllegalStateException("expression already set to " + e)
			e = NonValueClause(leftAlias, left, op, rightAlias, right)
			this
		}

		def and(leftAlias: String, left: String, op: String, rightAlias: String, right: String) = {
			val nvc = NonValueClause(leftAlias, left, op, rightAlias, right)
			if (e == null)
				e = nvc
			else e = And(e, nvc)
			this
		}

		def apply(e: Expression) = {
			if (this.e != null) throw new IllegalStateException("expression already set to " + this.e)
			this.e = e
			this
		}

		def toSql = {
			val sb = new StringBuilder("inner join ")
			sb append table.toSql
			//			if (schema.isDefined) sb append schema.get append "."
			//			sb append table.tableName append " "
			//			if (table.alias != null) sb append table.alias append " "
			//			if (table.hints != null) sb append table.hints append " "
			sb append " on " append e.toSql
			sb.toString
		}

		def toValues = e.toValues

		override def toString = toSql

		override def equals(o: Any) = o match {
			case jb: InnerJoinBuilder => jb.toSql == toSql
			case _ => false
		}
	}

	class WhereBuilder(e: Expression)
	{
		def toValues = e.toValues

		def toSql = e match {
			case null => throw new IllegalStateException("where with no clauses! Did you declare primary keys for your entity? Did you declare 1 or more where clauses?")
			case _ => "where " + e.toSql
		}

		override def toString = "WhereBuilder(%s)".format(e.toSql)
	}

	case class Result(sql: String, values: List[SqlParameterValue])

	class SqlSelectBuilder extends FromClause
	{
		private var cols = List[String]()
		private var fromClause: FromClause = null
		private var fromClauseAlias: String = null
		private var innerJoins = List[InnerJoinBuilder]()
		private var orderByBuilder: Option[OrderByBuilder] = None
		private var atTheEnd = List[String]()

		private var whereBuilder: Option[WhereBuilder] = None

		def columns(alias: String, cs: List[SimpleColumn]): this.type =
			columnNames(alias, cs.map(_.name))

		def columnNames(alias: String, cs: List[String]): this.type = {
			cols = cols ::: cs.map((if (alias != null) alias + "." else "") + escapeNamesStrategy.escapeColumnNames(_))
			this
		}

		def from = fromClause

		def from(from: FromClause): this.type = {
			this.fromClause = from
			this
		}

		def from(schema: Option[String], schemaModifications: SchemaModifications, table: String): this.type = from(schema, schemaModifications, table, null, null)

		def from(fromClause: SqlSelectBuilder, alias: String): this.type = {
			from(fromClause)
			fromClauseAlias = alias
			this
		}

		def from(schema: Option[String], schemaModifications: SchemaModifications, table: String, alias: String, hints: String): this.type = {
			if (fromClause != null) throw new IllegalStateException("from already called for %s".format(from))
			fromClause = Table(schema, schemaModifications, table, alias, hints)
			this
		}

		def where(alias: String, columnsAndValues: List[(SimpleColumn, Any)], op: String) = {
			if (whereBuilder.isDefined) throw new IllegalStateException("where already defined")
			whereBuilder = Some(SqlBuilder.this.whereAll(alias, columnsAndValues, op))
			this
		}

		def where(alias: String, column: SimpleColumn, op: String, value: Any) = {
			if (whereBuilder.isDefined) throw new IllegalStateException("where already defined")
			whereBuilder = Some(new WhereBuilder(Clause(alias, column, op, value)))
			this
		}

		def where(e: Expression) = {
			if (whereBuilder.isDefined) throw new IllegalStateException("where already defined")
			whereBuilder = Some(new WhereBuilder(e))
			this
		}

		def appendSql(sql: String) = {
			atTheEnd = sql :: atTheEnd
			this
		}

		def innerJoin(ijb: InnerJoinBuilder) = {
			if (!innerJoins.contains(ijb))
				innerJoins = ijb :: innerJoins
			this
		}

		def innerJoin(table: Table) = {
			val ijb = new InnerJoinBuilder(table)
			innerJoins = ijb :: innerJoins
			ijb
		}

		def orderBy(obb: OrderByBuilder) = {
			orderByBuilder = Some(obb)
			this
		}

		def result = Result(toSql, toValues)

		def toValues: List[SqlParameterValue] = innerJoins.map {
			_.toValues
		}.flatten ::: fromClause.toValues ::: whereBuilder.map(_.toValues).getOrElse(Nil)

		def toSql = {
			if (fromClause == null) throw new IllegalStateException("fromClause is null")
			val s = new StringBuilder("select ")
			s append cols.map(n => escapeNamesStrategy.escapeColumnNames(n)).mkString(",") append "\n"
			s append "from " append (fromClause match {
				case t: Table => t.toSql
				case s: SqlSelectBuilder =>
					val fromPar = "(" + s.toSql + ")"
					if (fromClauseAlias == null) fromPar else fromPar + " as " + fromClauseAlias
			}) append "\n"
			innerJoins.reverse.foreach {
				j =>
					s append j.toSql append "\n"
			}
			whereBuilder.foreach(s append _.toSql append "\n")
			orderByBuilder.foreach(s append _.toSql append "\n")
			if (!atTheEnd.isEmpty) s append atTheEnd.reverse.mkString("\n")
			s.toString()
		}

		override def toString = "SqlSelectBuilder(" + toSql + ")"
	}

	def whereAll(alias: String, columnsAndValues: List[(SimpleColumn, Any)], op: String): WhereBuilder =
		new WhereBuilder(columnsAndValues.foldLeft[Expression](null) {
			case (prevClause, (column, value)) =>
				val clause = Clause(alias, column, op, value)
				prevClause match {
					case null => clause
					case _ => And(prevClause, clause)
				}
		})

	case class OrderByExpression(column: String, ascDesc: String)
	{
		def toSql = escapeNamesStrategy.escapeColumnNames(column) + " " + ascDesc
	}

	class OrderByBuilder(expressions: List[OrderByExpression])
	{
		def toSql = "order by " + expressions.map(_.toSql).mkString(",")
	}

	class DeleteBuilder
	{
		private var fromClause: FromClause = null
		private var whereBuilder: WhereBuilder = null

		def from(schema: Option[String], schemaModifications: SchemaModifications, table: String): this.type = from(Table(schema, schemaModifications, table))

		def from(fromClause: FromClause): this.type = {
			this.fromClause = fromClause
			this
		}

		def where(whereBuilder: WhereBuilder): this.type = {
			this.whereBuilder = whereBuilder
			this
		}

		def where(columnsAndValues: List[(SimpleColumn, Any)], op: String): this.type =
			where(whereAll(null, columnsAndValues, "="))

		def result = Result(toSql, toValues)

		def toSql = "delete from %s %s".format(fromClause.toSql, if (whereBuilder == null) "" else whereBuilder.toSql)

		def toValues: List[SqlParameterValue] = if (whereBuilder == null) Nil else whereBuilder.toValues

		override def toString = "DeleteBuilder(%s)".format(toSql)
	}

	class InsertBuilder
	{
		private var table: Table = null
		private var cvs: List[(SimpleColumn, Any)] = Nil
		private var css: List[(SimpleColumn, String)] = Nil

		def into(table: Table): this.type = {
			this.table = table
			this
		}

		def into(schema: Option[String], schemaModifications: SchemaModifications, table: String): this.type = {
			into(Table(schema, schemaModifications, table))
			this
		}

		def columnAndSequences(css: List[(SimpleColumn, String)]) = {
			this.css = this.css ::: css
			this
		}

		def columnAndValues(cvs: List[(SimpleColumn, Any)]) = {
			this.cvs = this.cvs ::: cvs
			this
		}

		//insert into %s(%s) values(%s)
		def toSql = ("insert into " +
			table.tableName
			+ "("
			+ (
			css.map {
				case (c, s) => escapeNamesStrategy.escapeColumnNames(c.name)
			} ::: cvs.map {
				case (c, v) => escapeNamesStrategy.escapeColumnNames(c.name)
			}
			).mkString(",")
			+ ") values(" +
			(
				css.map {
					case (c, s) => s
				} ::: cvs.map(cv => "?")
				).mkString(",")
			+ ")"
			)

		def toValues: List[SqlParameterValue] = Jdbc.toSqlParameter(
			driver,
			cvs.map {
				case (c, v) =>
					(c.tpe, v)
			})

		def result = Result(toSql, toValues)
	}

	class UpdateBuilder
	{
		private var table: Table = null
		private var columnAndValues = List[(SimpleColumn, Any)]()
		private var where: WhereBuilder = null
		private var expression: Expression = EmptyExpression

		def table(schema: Option[String], schemaModifications: SchemaModifications, name: String): this.type = table(Table(schema, schemaModifications, name))

		def table(table: Table): this.type = {
			this.table = table
			this
		}

		def set(e: Expression): this.type = {
			expression = e
			this
		}

		def set(columnAndValues: List[(SimpleColumn, Any)]): this.type = {
			this.columnAndValues = columnAndValues
			this
		}

		def where(where: WhereBuilder): this.type = {
			if (this.where != null) throw new IllegalStateException("where already set to " + this.where)
			this.where = where
			this
		}

		def where(e: Expression): this.type = where(new WhereBuilder(e))

		def where(columnsAndValues: List[(SimpleColumn, Any)], op: String): this.type =
			where(whereAll(null, columnsAndValues, op))

		def result = Result(toSql, toValues)

		def toSql = (
			"update "
				+ table.tableName
				+ "\nset "
				+ columnAndValues.map {
				case (c, v) =>
					escapeNamesStrategy.escapeColumnNames(c.name) + " = ?"
			}.mkString(",")
				+ expression.toSql
				+ "\n"
				+ (if (where != null) where.toSql else "")
			)

		def toValues = {
			val params = Jdbc.toSqlParameter(
				driver,
				columnAndValues.map {
					case (c, v) =>
						(c.tpe, v)
				}) ::: expression.toValues
			if (where != null) params ::: where.toValues else params
		}
	}

}