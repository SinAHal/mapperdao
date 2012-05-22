package com.googlecode.mapperdao

/**
 * @author kostantinos.kougios
 *
 * 22 May 2012
 */
class UnlinkEntityRelationshipVisitor extends EntityRelationshipVisitor(visitLazyLoaded = true) {
	def manyToMany[T, F](ci: ColumnInfoTraversableManyToMany[T, _, F], traversable: Traversable[F]) = {
		traversable.foreach(unlink(_))
		None
	}
	def oneToMany[T, F](ci: ColumnInfoTraversableOneToMany[T, _, F], traversable: Traversable[F]) = {
		traversable.foreach(unlink(_))
		None
	}
	def manyToOne[T, F](ci: ColumnInfoManyToOne[T, _, F], foreign: F) = {
		unlink(foreign)
		None
	}
	def oneToOne[T, F](ci: ColumnInfoOneToOne[T, _, _], foreign: F) = {
		unlink(foreign)
		None
	}
	def oneToOneReverse[T, F](ci: ColumnInfoOneToOneReverse[T, _, _], foreign: F) = {
		unlink(foreign)
		None
	}

	def unlink(o: Any) = o match {
		case p: Persisted =>
			p.mapperDaoDiscarded = true
			p.mapperDaoValuesMap = null
		case _ =>
	}
}