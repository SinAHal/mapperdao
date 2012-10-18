package com.googlecode.mapperdao

import org.joda.time.DateTime

/**
 * query builder and DSL
 *
 * typical usage is to
 *
 * import Query._
 *
 * val pe=ProductEntity
 * val jeans=(select
 * 		from pe
 * 		where pe.title==="jeans").toList
 *
 * The import makes sure the implicits and builders for the DSL can be used.
 * All classes of this object are internal API of mapperdao and can not be
 * used externally.
 *
 * Compilation errors sometimes can be tricky to understand but this is common
 * with DSL's. Please read the examples on the wiki pages or go through the
 * mapperdao examples / test suites.
 *
 * @author kostantinos.kougios
 *
 * 15 Aug 2011
 */
object Query extends SqlImplicitConvertions {

	// starting point of a query, "select" syntactic sugar
	def select[ID, PC <: DeclaredIds[ID], T] = new QueryFrom[ID, PC, T]

	// "from" syntactic sugar
	protected class QueryFrom[ID, PC <: DeclaredIds[ID], T] {
		def from(entity: Entity[ID, PC, T]) = new Builder(entity)
	}

	trait OrderBy[Q] { self: Q =>
		protected def addOrderBy(l: List[(ColumnInfo[_, _], AscDesc)])

		def orderBy(byList: (ColumnInfo[_, _], AscDesc)*) =
			{
				addOrderBy(byList.toList)
				self
			}

		def orderBy[T, V](ci: ColumnInfo[T, V]) =
			{
				addOrderBy(List((ci, asc)))
				self
			}
		def orderBy[T, V](ci: ColumnInfo[T, V], ascDesc: AscDesc) =
			{
				addOrderBy(List((ci, ascDesc)))
				self
			}

		def orderBy[T1, V1, T2, V2](ci1: ColumnInfo[T1, V1], ci2: ColumnInfo[T2, V2]) =
			{
				addOrderBy(List((ci1, asc), (ci2, asc)))
				self
			}

		def orderBy[T1, V1, T2, V2](ci1: ColumnInfo[T1, V1], ascDesc1: AscDesc, ci2: ColumnInfo[T2, V2], ascDesc2: AscDesc) =
			{
				addOrderBy(List((ci1, ascDesc1), (ci2, ascDesc2)))
				self
			}
	}

	/**
	 * main query builder, keeps track of all 'where', joins and order by.
	 */
	class Builder[ID, PC <: DeclaredIds[ID], T](protected[mapperdao] val entity: Entity[ID, PC, T]) extends OrderBy[Builder[ID, PC, T]] {
		protected[mapperdao] var wheres = List[Where[ID, PC, T]]()
		protected[mapperdao] var joins = List[Any]()
		protected[mapperdao] var order = List[(ColumnInfo[_, _], AscDesc)]()

		override protected def addOrderBy(l: List[(ColumnInfo[_, _], AscDesc)]) {
			order :::= l
		}

		def where = {
			val qe = new Where(this)
			wheres ::= qe
			qe
		}

		def join[JID, JPC <: DeclaredIds[JID], JT, FID, FPC <: DeclaredIds[FID], FT](
			joinEntity: Entity[JID, JPC, JT],
			ci: ColumnInfoRelationshipBase[JT, _, FID, FPC, FT],
			foreignEntity: Entity[FID, FPC, FT]) = {
			val j = new Join(joinEntity, ci, foreignEntity)
			joins ::= j
			this
		}

		def join[JID, JPC <: DeclaredIds[JID], JT](entity: Entity[JID, JPC, JT]) = {
			val on = new JoinOn(this)
			val j = new SJoin(entity, on)
			joins ::= j
			on
		}

		def toList(implicit queryDao: QueryDao): List[T with PC] = toList(QueryConfig.default)(queryDao)
		def toList(queryConfig: QueryConfig)(implicit queryDao: QueryDao): List[T with PC] = queryDao.query(queryConfig, this)

		def toSet(implicit queryDao: QueryDao): Set[T with PC] = toSet(QueryConfig.default)(queryDao)
		def toSet(queryConfig: QueryConfig)(implicit queryDao: QueryDao): Set[T with PC] = queryDao.query(queryConfig, this).toSet

		override def toString = "select from %s join %s where %s".format(entity, joins, wheres)
	}

	sealed abstract class AscDesc {
		val sql: String
	}
	object asc extends AscDesc {
		val sql = "asc"
	}
	object desc extends AscDesc {
		val sql = "desc"
	}

	protected[mapperdao] case class Join[JID, JPC <: DeclaredIds[JID], JT, FID, FPC <: DeclaredIds[FID], FT](
		val joinEntity: Entity[JID, JPC, JT],
		val ci: ColumnInfoRelationshipBase[JT, _, FID, FPC, FT],
		val foreignEntity: Entity[FID, FPC, FT])
	protected[mapperdao] case class SJoin[JID, JPC <: DeclaredIds[JID], JT, FID, FPC <: DeclaredIds[FID], FT, QID, QPC <: DeclaredIds[QID], QT](
		// for join on functionality
		val entity: Entity[JID, JPC, JT],
		val on: JoinOn[QID, QPC, QT])

	protected[mapperdao] class JoinOn[ID, PC <: DeclaredIds[ID], T](protected[mapperdao] val queryEntity: Builder[ID, PC, T]) {
		protected[mapperdao] var ons = List[Where[ID, PC, T]]()
		def on =
			{
				val qe = new Where(queryEntity)
				ons ::= qe
				qe
			}
	}

	protected[mapperdao] class Where[ID, PC <: DeclaredIds[ID], T](
		protected[mapperdao] val queryEntity: Builder[ID, PC, T])
			extends OrderBy[Where[ID, PC, T]]
			with SqlWhereMixins[Where[ID, PC, T]] {

		override def addOrderBy(l: List[(ColumnInfo[_, _], AscDesc)]) {
			queryEntity.order :::= l
		}

		def where = {
			val qe = new Where(queryEntity)
			queryEntity.wheres ::= qe
			qe
		}

		def toList(implicit queryDao: QueryDao): List[T with PC] = toList(QueryConfig.default)(queryDao)
		def toList(queryConfig: QueryConfig)(implicit queryDao: QueryDao): List[T with PC] = queryDao.query(queryConfig, this)

		def toSet(implicit queryDao: QueryDao): Set[T with PC] = toSet(QueryConfig.default)(queryDao)
		def toSet(queryConfig: QueryConfig)(implicit queryDao: QueryDao): Set[T with PC] = queryDao.query(queryConfig, this).toSet

		override def toString = "Where(%s)".format(clauses)
	}
}
