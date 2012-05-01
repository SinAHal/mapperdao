package com.googlecode.mapperdao.utils

import com.googlecode.mapperdao.Entity
import com.googlecode.mapperdao.Persisted
import com.googlecode.mapperdao.SimpleTypeValue

/**
 * a map that holds entities. Persisted entities
 * are matched against their primary keys and
 * non persisted against their identity hash
 * code.
 *
 * @author kostantinos.kougios
 *
 * 30 Apr 2012
 */
protected class EntityMap[PC, T](entity: Entity[PC, T], keyMode: EntityMap.EqualsMode[T]) {
	private val table = entity.tpe.table
	private var m = Map[Any, T]()

	def add(o: T): Unit =
		m = m + (key(o) -> o)

	def addAll(l: Traversable[T]): Unit = l foreach { o => add(o) }
	def contains(o: T) = m.contains(key(o))

	private def key(o: T) = keyMode.key(o)
}

protected object EntityMap {
	abstract class EqualsMode[T] {
		def key(o: T): Any
	}
	class EntityEquals[T](entity: Entity[_, T]) extends EqualsMode[T] {
		override def key(o: T) = o match {
			case p: Persisted =>
				val table = entity.tpe.table
				table.toListOfPrimaryKeyAndValueTuples(o) ::: table.toListOfColumnAndValueTuplesForUnusedKeys(o)
			case _ =>
				System.identityHashCode(o)
		}
	}
	class ByObjectEquals[T] extends EqualsMode[T] {
		override def key(o: T) = o
	}
}