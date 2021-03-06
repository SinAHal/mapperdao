package com.googlecode.mapperdao.sqlfunction

import com.googlecode.mapperdao.schema.Schema


/**
 * represents a database function and can be used in queries
 *
 * @author kostantinos.kougios
 *
 *         5 Sep 2012
 */
object SqlFunction
{
	/**
	 * 1 arg function. V1 is the type of the first arg and R the return type
	 */
	def with1Arg[V1, R](name: String, schema: Option[Schema] = None) = new SqlFunctionValue1[V1, R](name, schema)

	/**
	 * 2 arg function. V1,V2 are the types of the first and second args
	 * and R the return type
	 */
	def with2Args[V1, V2, R](name: String, schema: Option[Schema] = None) = new SqlFunctionValue2[V1, V2, R](name, schema)

	/**
	 * 3 arg function. V1,V2,V3 are the types of the first,second and third args
	 * and R the return type
	 */
	def with3Args[V1, V2, V3, R](name: String, schema: Option[Schema] = None) = new SqlFunctionValue3[V1, V2, V3, R](name, schema)

	/**
	 * 4 arg function. V1,V2,V3,V4 are the types of the first,second,third and fourth args
	 * and R the return type
	 */
	def with4Args[V1, V2, V3, V4, R](name: String, schema: Option[Schema] = None) = new SqlFunctionValue4[V1, V2, V3, V4, R](name, schema)

	/**
	 * 5 arg function. V1,V2,V3,V4,V5 are the types of the args
	 * and R the return type
	 */
	def with5Args[V1, V2, V3, V4, V5, R](name: String, schema: Option[Schema] = None) = new SqlFunctionValue5[V1, V2, V3, V4, V5, R](name, schema)
}