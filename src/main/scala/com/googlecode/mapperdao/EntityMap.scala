package com.googlecode.mapperdao

import java.util.IdentityHashMap
import com.googlecode.mapperdao.jdbc.JdbcMap
import scala.collection.mutable.{ HashMap => TMap }

/**
 * contains entities sorted via 2 keys: class and ids
 *
 * @author kostantinos.kougios
 *
 * 7 Aug 2011
 */
private[mapperdao] case class EntityMap(
		private val m: TMap[List[Any], Option[_]] = TMap(),
		private val parent: SelectInfo[Any, DeclaredIds[Any], Any, Any, Any, DeclaredIds[Any], Any] = SelectInfo(null, null, null)) {

	protected def key(clz: Class[_], ids: List[Any]) = clz :: ids

	def putMock[T](clz: Class[_], ids: List[Any], entity: T): Unit =
		{
			val k = key(clz, ids)
			m.synchronized {
				if (m.contains(k)) {
					// mocks should only "put" if the map doesn't already have a value
					throw new IllegalStateException("ids %s already contained for %s".format(ids, clz))
				} else {
					m(k) = Some(entity)
				}
			}
		}

	def justGet[T](clz: Class[_], ids: List[Any]): Option[T] = {
		val k = key(clz, ids)
		m.synchronized {
			m.get(k).getOrElse(None)
		}.asInstanceOf[Option[T]]
	}

	def get[T](clz: Class[_], ids: List[Any])(f: => Option[T]): Option[T] = {
		val k = key(clz, ids)
		m.synchronized {
			m.getOrElse(k, {
				val vo = f
				m(k) = vo
				vo
			})
		}.asInstanceOf[Option[T]]
	}

	def down[ID, PC <: DeclaredIds[ID], T, V, FID, FPC <: DeclaredIds[FID], F](
		selectConfig: SelectConfig,
		tpe: Type[ID, PC, T],
		ci: ColumnInfoRelationshipBase[T, V, FID, FPC, F],
		dv: DatabaseValues): EntityMap =
		copy(parent = SelectInfo(tpe.asInstanceOf[Type[Any, com.googlecode.mapperdao.DeclaredIds[Any], Any]], ci.asInstanceOf[ColumnInfoRelationshipBase[Any, Any, Any, DeclaredIds[Any], Any]], dv))

	def peek[ID, PC <: DeclaredIds[ID], T, V, FID, FPC <: DeclaredIds[FID], F] =
		parent.asInstanceOf[SelectInfo[ID, PC, T, V, FID, FPC, F]]

	override def toString = "EntityMapImpl(%s)".format(m.toString)
}

protected case class SelectInfo[ID, PC <: DeclaredIds[ID], T, V, FID, FPC <: DeclaredIds[FID], F](
	val tpe: Type[ID, PC, T],
	val ci: ColumnInfoRelationshipBase[T, V, FID, FPC, F],
	val databaseValues: DatabaseValues)
