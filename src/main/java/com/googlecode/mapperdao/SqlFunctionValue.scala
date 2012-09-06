package com.googlecode.mapperdao

/**
 * @author kostantinos.kougios
 *
 * 6 Sep 2012
 */
protected case class SqlFunctionValue[R](name: String, values: List[Any]) {
	def ===(v: SqlFunctionArg[R]) = new SqlFunctionOp(this, EQ(), v.v)
}

object SqlFunctionValue {
	implicit def columnInfoSqlFunctionOperation[R](values: SqlFunctionValue[R]) = new SqlFunctionBoolOp[R](values)
}
/**
 * function with 1 parameter
 */
protected class SqlFunctionValue1[V1, R](name: String) {
	def apply(v1: SqlFunctionArg[V1]) = SqlFunctionValue[R](name, List(v1.v))
}
/**
 * function with 2 parameters
 */
protected class SqlFunctionValue2[V1, V2, R](name: String) {
	def apply(v1: SqlFunctionArg[V1], v2: SqlFunctionArg[V2]) = SqlFunctionValue[R](name, List(v1.v, v2.v))
}

case class SqlFunctionOp[V, R](left: SqlFunctionValue[R], operand: Operand, right: V) extends OpBase

case class SqlFunctionBoolOp[R](bop: SqlFunctionValue[R]) extends OpBase

/**
 * we need an arg class so that we can both pass a value to a function
 * or a compatible columninfo.
 */
class SqlFunctionArg[V](val v: Any)
object SqlFunctionArg {
	implicit def anyToArg[T](v: T) = new SqlFunctionArg[T](v)
	implicit def columnInfoToArg[T](v: ColumnInfo[_, T]) = new SqlFunctionArg[T](v)
}