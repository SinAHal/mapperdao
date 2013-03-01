package com.googlecode.mapperdao

import com.googlecode.mapperdao.utils.LazyActions
import com.googlecode.mapperdao.utils.MapWithDefault

/**
 * external entities allow loading entities via a custom dao or i.e. hibernate,
 * a json call, loading it from a file etc.
 *
 * T is the type of the entity.
 *
 * If queries with joins are to be done for this entity and the entity has a table (but is mapped with an other
 * orm or is just loaded via jdbc) then the table and columns can be mapped as normally would be done if the
 * entity was a mapperdao entity.
 *
 * In the following example, ProductEntity is loaded via mapperdao but AttributeEntity is
 * loaded from an external data source (in this case it is just generated)
 *
 * <code>
 * object ProductEntity extends Entity[IntId, Product] {
 * val id = key("id") autogenerated (_.id)
 * val name = column("name") to (_.name)
 * val attributes = manytomany(AttributeEntity) to (_.attributes)
 *
 * def constructor(implicit m) = new Product(name, attributes) with IntId with Persisted {
 * val id: Int = ProductEntity.id
 * }
 * }
 *
 * object AttributeEntity extends ExternalEntity[Attribute] {
 *
 * onInsertManyToMany(ProductEntity.attributes) { i =>
 * PrimaryKeysValues(i.foreign.id)
 * }
 *
 * onSelectManyToMany(ProductEntity.attributes) { s =>
 * s.foreignIds.map {
 * case (id: Int) :: Nil =>
 * Attribute(id, "x" + id)
 * case _ => throw new RuntimeException
 * }
 * }
 *
 * onUpdateManyToMany(ProductEntity.attributes) { u =>
 * PrimaryKeysValues(u.foreign.id)
 * }
 *
 * onDeleteManyToMany { d =>
 * }
 * }
 * </code>
 *
 */
abstract class ExternalEntity[FID, F](table: String, clz: Class[F]) extends Entity[FID, F](table, clz) {
	def this()(implicit m: ClassManifest[F]) = this(m.erasure.getSimpleName, m.erasure.asInstanceOf[Class[F]])

	def this(table: String)(implicit m: ClassManifest[F]) = this(table, m.erasure.asInstanceOf[Class[F]])

	private val lazyActions = new LazyActions[Unit]

	override def constructor(implicit m: ValuesMap) = throw new IllegalStateException("constructor shouldn't be called for ExternalEntity %s".format(clz))

	/**
	 * support for many-to-many mapping
	 */
	type OnSelectManyToMany[T] = SelectExternalManyToMany => List[F]
	type OnUpdateManyToMany[T] = ExternalManyToMany[F] => Unit
	private[mapperdao] val manyToManyOnSelectMap = new MapWithDefault[ColumnInfoTraversableManyToMany[_, _, F], OnSelectManyToMany[_]]("onSelectManyToMany must be called for External Entity %s".format(getClass.getName))
	private[mapperdao] val manyToManyOnUpdateMap = new MapWithDefault[ColumnInfoTraversableManyToMany[_, _, F], OnUpdateManyToMany[_]]("onUpdateManyToMany must be called for External Entity %s".format(getClass.getName))

	def onSelectManyToMany[T](ci: => ColumnInfoTraversableManyToMany[T, _, F])(handler: OnSelectManyToMany[T]) = lazyActions(() => manyToManyOnSelectMap +(ci, handler))

	def onSelectManyToMany(handler: OnSelectManyToMany[Any]) {
		manyToManyOnSelectMap.default = Some(handler)
	}

	def onUpdateManyToMany[T](ci: => ColumnInfoTraversableManyToMany[T, _, F])(handler: OnUpdateManyToMany[T]) = lazyActions(() => manyToManyOnUpdateMap +(ci, handler))

	def onUpdateManyToMany(handler: OnUpdateManyToMany[Any]) {
		manyToManyOnUpdateMap.default = Some(handler)
	}

	/**
	 * support for one-to-one reverse mapping
	 */
	type OnInsertOneToOneReverse[T] = InsertExternalOneToOneReverse[T, F] => Unit
	type OnSelectOneToOneReverse[T] = SelectExternalOneToOneReverse[T, F] => F
	type OnUpdateOneToOneReverse[T] = UpdateExternalOneToOneReverse[T, F] => Unit
	type OnDeleteOneToOneReverse[T] = DeleteExternalOneToOneReverse[T, F] => Unit
	private[mapperdao] val oneToOneOnInsertMap = new MapWithDefault[ColumnInfoOneToOneReverse[_, _, F], OnInsertOneToOneReverse[_]]("onInsertOneToOneReverse must be called for External Entity %s".format(getClass.getName))
	private[mapperdao] val oneToOneOnSelectMap = new MapWithDefault[ColumnInfoOneToOneReverse[_, _, F], OnSelectOneToOneReverse[_]]("onSelectOneToOneReverse must be called for External Entity %s".format(getClass.getName))
	private[mapperdao] val oneToOneOnUpdateMap = new MapWithDefault[ColumnInfoOneToOneReverse[_, _, F], OnUpdateOneToOneReverse[_]]("onUpdateOneToOneReverse must be called for External Entity %s".format(getClass.getName))
	private[mapperdao] val oneToOneOnDeleteMap = new MapWithDefault[ColumnInfoOneToOneReverse[_, _, F], OnDeleteOneToOneReverse[_]]("onDeleteOneToOneReverse must be called for External Entity %s".format(getClass.getName))

	def onInsertOneToOneReverse[T](ci: => ColumnInfoOneToOneReverse[T, _, F])(handler: OnInsertOneToOneReverse[T]) = lazyActions(() => oneToOneOnInsertMap +(ci, handler))

	def onInsertOneToOneReverse[T](handler: OnInsertOneToOneReverse[T]) {
		oneToOneOnInsertMap.default = Some(handler)
	}

	def onSelectOneToOneReverse[T](ci: => ColumnInfoOneToOneReverse[T, _, F])(handler: OnSelectOneToOneReverse[T]) = lazyActions(() => oneToOneOnSelectMap +(ci, handler))

	def onSelectOneToOneReverse(handler: OnSelectOneToOneReverse[Any]) {
		oneToOneOnSelectMap.default = Some(handler)
	}

	def onUpdateOneToOneReverse[T](ci: => ColumnInfoOneToOneReverse[T, _, F])(handler: OnUpdateOneToOneReverse[T]) = lazyActions(() => oneToOneOnUpdateMap +(ci, handler))

	def onUpdateOneToOneReverse(handler: OnUpdateOneToOneReverse[Any]) {
		oneToOneOnUpdateMap.default = Some(handler)
	}

	def onDeleteOneToOneReverse[T](ci: => ColumnInfoOneToOneReverse[T, _, F])(handler: OnDeleteOneToOneReverse[T]) = lazyActions(() => oneToOneOnDeleteMap +(ci, handler))

	def onDeleteOneToOneReverse(handler: OnDeleteOneToOneReverse[Any]) {
		oneToOneOnDeleteMap.default = Some(handler)
	}

	/**
	 * support for many-to-one mapping
	 */
	type OnSelectManyToOne[T] = SelectExternalManyToOne[F] => F
	type OnUpdateManyToOne[T] = UpdateExternalManyToOne[F] => Unit
	type OnDeleteManyToOne = DeleteExternalManyToOne[F] => Unit

	private[mapperdao] val manyToOneOnSelectMap = new MapWithDefault[ColumnInfoManyToOne[_, _, F], OnSelectManyToOne[_]]("onSelectManyToOne must be called for External Entity %s".format(getClass.getName))
	private[mapperdao] val manyToOneOnUpdateMap = new MapWithDefault[ColumnInfoManyToOne[_, _, F], OnUpdateManyToOne[_]]("onUpdateManyToOne must be called for External Entity %s".format(getClass.getName))
	private[mapperdao] val manyToOneOnDeleteMap = new MapWithDefault[ColumnInfoManyToOne[_, _, F], OnDeleteManyToOne]("onDeleteManyToOne must be called for External Entity %s".format(getClass.getName))

	def onSelectManyToOne[T](ci: => ColumnInfoManyToOne[T, _, F])(handler: OnSelectManyToOne[T]) = lazyActions(() => manyToOneOnSelectMap +(ci, handler))

	def onSelectManyToOne(handler: OnSelectManyToOne[Any]) {
		manyToOneOnSelectMap.default = Some(handler)
	}

	def onUpdateManyToOne[T](ci: => ColumnInfoManyToOne[T, _, F])(handler: OnUpdateManyToOne[T]) = lazyActions(() => manyToOneOnUpdateMap +(ci, handler))

	def onUpdateManyToOne(handler: OnUpdateManyToOne[Any]) {
		manyToOneOnUpdateMap.default = Some(handler)
	}

	def onDeleteManyToOne[T](ci: => ColumnInfoManyToOne[T, _, F])(handler: OnDeleteManyToOne) = lazyActions(() => manyToOneOnDeleteMap +(ci, handler))

	def onDeleteManyToOne(handler: OnDeleteManyToOne) {
		manyToOneOnDeleteMap.default = Some(handler)
	}

	/**
	 * support for one-to-many mapping
	 */
	type OnSelectOneToMany = SelectExternalOneToMany => List[F]
	type OnUpdateOneToMany[T] = UpdateExternalOneToMany[F] => Unit
	type OnDeleteOneToMany[T] = DeleteExternalOneToMany[T, F] => Unit
	private[mapperdao] val oneToManyOnSelectMap = new MapWithDefault[ColumnInfoTraversableOneToMany[_, _, _, F], OnSelectOneToMany]("onSelectOneToMany must be called for External Entity %s".format(getClass.getName))
	private[mapperdao] val oneToManyOnUpdateMap = new MapWithDefault[ColumnInfoTraversableOneToMany[_, _, _, F], OnUpdateOneToMany[_]]("onUpdateOneToMany must be called for External Entity %s".format(getClass.getName))
	private[mapperdao] val oneToManyOnDeleteMap = new MapWithDefault[ColumnInfoTraversableOneToMany[_, _, _, F], OnDeleteOneToMany[_]]("onUpdateOneToMany must be called for External Entity %s".format(getClass.getName))

	def onSelectOneToMany(ci: => ColumnInfoTraversableOneToMany[_, _, _, F])(handler: OnSelectOneToMany) = lazyActions(() => oneToManyOnSelectMap +(ci, handler))

	def onSelectOneToMany(handler: OnSelectOneToMany) = oneToManyOnSelectMap.default = Some(handler)

	def onUpdateOneToMany[T](ci: => ColumnInfoTraversableOneToMany[_, T, _, F])(handler: OnUpdateOneToMany[T]) = lazyActions(() => oneToManyOnUpdateMap +(ci, handler))

	def onUpdateOneToMany(handler: OnUpdateOneToMany[Any]) = oneToManyOnUpdateMap.default = Some(handler)

	def onDeleteOneToMany[T](ci: => ColumnInfoTraversableOneToMany[_, T, _, F])(handler: OnDeleteOneToMany[T]) = lazyActions(() => oneToManyOnDeleteMap +(ci, handler))

	def onDeleteOneToMany(handler: OnDeleteOneToMany[Any]) = oneToManyOnDeleteMap.default = Some(handler)

	override def init: Unit = {
		super.init
		lazyActions.executeAll
	}
}

case class SelectExternalManyToMany(selectConfig: SelectConfig, foreignIds: List[List[Any]] /* a list of the id's as an other list */)

trait ExternalManyToMany[F]

/**
 * these case classes are passed on as parameters to external entities update handlers
 */
case class InsertExternalManyToMany[F](updateConfig: UpdateConfig, added: Traversable[F]) extends ExternalManyToMany[F]

case class UpdateExternalManyToMany[F](updateConfig: UpdateConfig, updated: Traversable[(F, F)]) extends ExternalManyToMany[F]

case class DeleteExternalManyToMany[F](deleteConfig: DeleteConfig, removed: Traversable[F]) extends ExternalManyToMany[F]

case class InsertExternalOneToOneReverse[T, F](updateConfig: UpdateConfig, entity: T, foreign: F)

case class SelectExternalOneToOneReverse[T, F](selectConfig: SelectConfig, foreignIds: List[Any])

case class UpdateExternalOneToOneReverse[T, F](updateConfig: UpdateConfig, entity: T, foreign: F)

case class DeleteExternalOneToOneReverse[T, F](deleteConfig: DeleteConfig, entity: T, foreign: F)

case class UpdateExternalManyToOne[F](updateConfig: UpdateConfig, foreign: F)

case class SelectExternalManyToOne[F](selectConfig: SelectConfig, primaryKeys: List[Any])

case class DeleteExternalManyToOne[F](deleteConfig: DeleteConfig, foreign: F)

case class SelectExternalOneToMany(selectConfig: SelectConfig, foreignIds: List[Any])

case class UpdateExternalOneToMany[F](
	updateConfig: UpdateConfig,
	newVM: ValuesMap,
	added: Traversable[F],
	intersection: Traversable[(F, F)],
	removed: Traversable[F]
	)

case class DeleteExternalOneToMany[T, F](deleteConfig: DeleteConfig, entity: T, many: Traversable[F])
