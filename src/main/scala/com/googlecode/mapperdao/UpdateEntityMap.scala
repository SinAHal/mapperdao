package com.googlecode.mapperdao

import scala.collection.immutable.Stack
import collection.mutable

protected class UpdateEntityMap {
	private val m = new mutable.HashMap[Int, Any]
	private var stack = Stack[UpdateInfo[_, _, _, _, _]]()

	def put[T](identity: Int, mock: DeclaredIds[_] with T): Unit = m.put(identity, mock)

	def get[T](identity: Int): Option[DeclaredIds[_] with T] = {
		val g = m.get(identity).asInstanceOf[Option[DeclaredIds[_] with T]]
		g
	}

	def down[PID, PT, V, FID, F](
		o: PT,
		ci: ColumnInfoRelationshipBase[PT, V, FID, F],
		parentEntity: Entity[PID, PT]
		): Unit =
		stack = stack.push(UpdateInfo(o, ci, parentEntity))

	def peek[PID, PT, V, FID, F] =
		(if (stack.isEmpty) UpdateInfo(null, null, null) else stack.top).asInstanceOf[UpdateInfo[PID, PT, V, FID, F]]

	def up = stack = stack.pop

	def done {
		if (!stack.isEmpty) throw new InternalError("stack should be empty but is " + stack)
	}

	def toErrorStr = {
		val sb = new StringBuilder
		stack.foreach {
			u =>
				sb append u.o append ('\n')
		}
		sb.toString
	}
}

protected case class UpdateInfo[PID, PT, V, FID, F](
	val o: PT,
	val ci: ColumnInfoRelationshipBase[PT, V, FID, F],
	parentEntity: Entity[PID, PT]
	)
