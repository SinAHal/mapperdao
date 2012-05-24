package com.googlecode.mapperdao

/**
 * @author kostantinos.kougios
 *
 * 6 May 2012
 */
private[mapperdao] class EntityMapImpl extends EntityMap with EntityStack {
	private val m = scala.collection.mutable.ListMap[List[Any], Option[_]]()

	protected def key(clz: Class[_], ids: List[Any]) = clz :: ids

	def putMock[T](clz: Class[_], ids: List[Any], entity: T): Unit = this.synchronized {
		{
			val k = key(clz, ids)
			if (m.contains(k)) {
				// mocks should only "put" if the map doesn't already have a value
				throw new IllegalStateException("ids %s already contained for %s".format(ids, clz))
			} else {
				m(k) = Some(entity)
			}
		}
	}

	def get[T](clz: Class[_], ids: List[Any])(f: => Option[T]): Option[T] = this.synchronized {
		val k = key(clz, ids)
		m.getOrElse(k, {
			val vo = f
			m(k) = vo
			vo
		}).asInstanceOf[Option[T]]
	}

	override def toString = "EntityMapImpl(%s)".format(m.toString)
}