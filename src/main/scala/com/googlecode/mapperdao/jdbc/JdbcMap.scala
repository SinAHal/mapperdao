package com.googlecode.mapperdao.jdbc

import org.joda.time.DateTime
import scala.collection.JavaConverters._

/**
 * a map of database column->value
 *
 * @author kostantinos.kougios
 *
 *         2 Aug 2011
 */
class JdbcMap(map: java.util.Map[String, _])
{
	private def get(key: String) = {
		val v = map.get(key)
		v
	}

	def toMap = map.asScala.toMap

	def apply(key: String): Any = get(key)

	def int(key: String): Int = get(key) match {
		case i: Int => i
		case l: Long => l.toInt
		case bd: java.math.BigDecimal => bd.intValue
	}

	def long(key: String): Long = get(key) match {
		case i: Int => i.toLong
		case l: Long => l
		case bd: java.math.BigDecimal => bd.longValue
	}

	def string(key: String): String = get(key).asInstanceOf[String]

	def datetime(key: String): DateTime = get(key).asInstanceOf[DateTime]

	def size: Int = map.size

	def isEmpty: Boolean = map.isEmpty

	override def toString = map.toString

	override def equals(v: Any) = v match {
		case m: Map[String, _] if (m.size == size) =>
			m.filter {
				case (k, value) =>
					val ov = map.get(k)
					val b = m(k) != ov && map.containsKey(k)
					b
			}.isEmpty
		case _ => false
	}
}