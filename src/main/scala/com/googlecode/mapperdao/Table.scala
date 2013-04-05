package com.googlecode.mapperdao

import com.googlecode.mapperdao.exceptions.ExpectedPersistedEntityException
import com.googlecode.mapperdao.schema._
import com.googlecode.mapperdao.schema.ColumnInfo
import com.googlecode.mapperdao.schema.Column

/**
 * mapping tables to entities
 *
 * this is internal mapperdao API.
 *
 * @author kostantinos.kougios
 *
 *         12 Jul 2011
 */

class Table[ID, T](
	val schema: Option[Schema],
	val name: String,
	val columnInfosPlain: List[ColumnInfoBase[T, _]],
	val extraColumnInfosPersisted: List[ColumnInfoBase[T with DeclaredIds[ID], _]],
	val unusedPKColumnInfos: List[ColumnInfoBase[Any, Any]]
	)
{

	val schemaName = schema.map(_.name)
	val columns: List[ColumnBase] = extraColumnInfosPersisted.map(_.column) ::: columnInfosPlain.map(_.column)
	// the primary keys for this table
	val primaryKeys: List[PK] = columns.collect {
		case pk: PK => pk
	}
	val unusedPKs = unusedPKColumnInfos.map {
		case ci: ColumnInfo[Any, Any] => List(ci.column)
		case ci: ColumnInfoManyToOne[Any, Any, Any] => ci.column.columns
		case ci: ColumnInfoTraversableOneToMany[Any, Any, Any, Any] => ci.column.columns
		case ci: ColumnInfoOneToOne[Any, Any, Any] => ci.column.columns
	}.flatten

	val primaryKeysAndUnusedKeys = primaryKeys ::: unusedPKs
	val primaryKeysSize = primaryKeysAndUnusedKeys.size

	val primaryKeyColumnInfosForT = columnInfosPlain.collect {
		case ci@ColumnInfo(_: PK, _, _) => ci
	}

	val primaryKeyColumnInfosForTWithPC = extraColumnInfosPersisted.collect {
		case ci@ColumnInfo(_: PK, _, _) => ci
	}

	val primaryKeysAsColumns = primaryKeys.map(k => Column(k.entity, k.name, k.tpe)).toSet

	val primaryKeysAsCommaSeparatedList = primaryKeys.map(_.name).mkString(",")

	val simpleTypeColumns: List[SimpleColumn] = columns.collect {
		case c: Column => c
		case pk: PK => pk
	}
	val relationshipColumns = columns.collect {
		case c: ColumnRelationshipBase[_, _] => c
	}
	val autoGeneratedColumns = simpleTypeColumns.filter(_.isAutoGenerated)
	val autoGeneratedColumnNamesArray = autoGeneratedColumns.map(_.name).toArray
	val columnsWithoutAutoGenerated = simpleTypeColumns.filterNot(_.isAutoGenerated) ::: relationshipColumns

	val simpleTypeSequenceColumns = simpleTypeColumns.filter(_.isSequence)
	val simpleTypeAutoGeneratedColumns = simpleTypeColumns.filter(_.isAutoGenerated)
	val simpleTypeNotAutoGeneratedColumns = simpleTypeColumns.filterNot(_.isAutoGenerated)

	val simpleTypeColumnInfos = columnInfosPlain.collect {
		case ci: ColumnInfo[T, _] => ci
	}

	val allRelationshipColumnInfos = columnInfosPlain.collect {
		case ci: ColumnInfoRelationshipBase[T, _, _, _] => ci
	}

	val allRelationshipColumnInfosSet = allRelationshipColumnInfos.toSet

	def relationshipColumnInfos(skip: Set[ColumnInfoRelationshipBase[_, _, _, _]]) = if (skip.isEmpty) {
		allRelationshipColumnInfos
	} else allRelationshipColumnInfos.filterNot(skip(_))

	val oneToOneColumns: List[OneToOne[Any, Any]] = columns.collect {
		case c: OneToOne[Any, Any] => c
	}

	val oneToOneReverseColumns: List[OneToOneReverse[Any, Any]] = columns.collect {
		case c: OneToOneReverse[Any, Any] => c
	}

	val oneToManyColumns: List[OneToMany[Any, Any]] = columns.collect {
		case c: OneToMany[Any, Any] => c
	}
	val manyToOneColumns: List[ManyToOne[Any, Any]] = columns.collect {
		case mto: ManyToOne[Any, Any] => mto
	}
	val manyToOneColumnsFlattened: List[Column] = columns.collect {
		case ManyToOne(_, columns: List[Column], _) => columns
	}.flatten

	val manyToManyColumns: List[ManyToMany[Any, Any]] = columns.collect {
		case c: ManyToMany[Any, Any] => c
	}

	val oneToOneColumnInfos: List[ColumnInfoOneToOne[T, Any, _]] = columnInfosPlain.collect {
		case c: ColumnInfoOneToOne[T, Any, _] => c
	}
	val oneToOneReverseColumnInfos: List[ColumnInfoOneToOneReverse[T, Any, _]] = columnInfosPlain.collect {
		case c: ColumnInfoOneToOneReverse[T, Any, _] => c
	}

	val oneToManyColumnInfos: List[ColumnInfoTraversableOneToMany[ID, T, Any, _]] = columnInfosPlain.collect {
		case c: ColumnInfoTraversableOneToMany[ID, T, Any, _] => c
	}
	val manyToOneColumnInfos: List[ColumnInfoManyToOne[T, Any, _]] = columnInfosPlain.collect {
		case c: ColumnInfoManyToOne[T, Any, _] => c
	}
	val manyToManyColumnInfos: List[ColumnInfoTraversableManyToMany[T, Any, _]] = columnInfosPlain.collect {
		case c: ColumnInfoTraversableManyToMany[T, Any, _] => c
	}

	val columnToColumnInfoMap: Map[ColumnBase, ColumnInfoBase[T, _]] = columnInfosPlain.map(ci => (ci.column, ci)).toMap
	val pcColumnToColumnInfoMap: Map[ColumnBase, ColumnInfoBase[T with DeclaredIds[ID], _]] = extraColumnInfosPersisted.map(ci => (ci.column, ci)).toMap

	val manyToManyToColumnInfoMap: Map[ColumnBase, ColumnInfoTraversableManyToMany[T, _, _]] = columnInfosPlain.collect {
		case c: ColumnInfoTraversableManyToMany[T, _, _] => (c.column, c)
	}.toMap

	val oneToManyToColumnInfoMap: Map[ColumnBase, ColumnInfoTraversableOneToMany[ID, T, Any, _]] = columnInfosPlain.collect {
		case c: ColumnInfoTraversableOneToMany[ID, T, Any, _] => (c.column, c)
	}.toMap

	def toListOfPrimaryKeyValues(o: T): List[Any] = toListOfPrimaryKeyAndValueTuples(o).map(_._2)

	def toListOfPrimaryKeyAndValueTuples(o: T): List[(PK, Any)] = toListOfColumnAndValueTuples(primaryKeys, o)

	def toListOfPrimaryKeySimpleColumnAndValueTuples(o: T): List[(SimpleColumn, Any)] = toListOfColumnAndValueTuples(primaryKeys, o)

	def toListOfUnusedPrimaryKeySimpleColumnAndValueTuples(o: Any): List[(SimpleColumn, Any)] =
		unusedPKColumnInfos.map {
			case ci: ColumnInfo[Any, Any] =>
				List((ci.column, ci.columnToValue(o)))
			case ci: ColumnInfoManyToOne[Any, Any, Any] =>
				val l = ci.columnToValue(o)
				val fe = ci.column.foreign.entity
				val pks = fe.tpe.table.toListOfPrimaryKeyValues(l)
				ci.column.columns zip pks
			case ci: ColumnInfoTraversableOneToMany[Any, Any, Any, Any] =>
				o match {
					case p: Persisted =>
						ci.column.columns map {
							c =>
								(c, p.mapperDaoValuesMap.columnValue[Any](c))
						}
					case _ => Nil
				}
			case ci: ColumnInfoOneToOne[Any, Any, Any] =>
				val l = ci.columnToValue(o)
				val fe = ci.column.foreign.entity
				val pks = fe.tpe.table.toListOfPrimaryKeyValues(l)
				ci.column.columns zip pks

			case ci: ColumnInfoRelationshipBase[Any, Any, Any, Any] => Nil
		}.flatten

	def toListOfColumnAndValueTuples[CB <: ColumnBase](columns: List[CB], o: T): List[(CB, Any)] = columns.map {
		c =>
			val ctco = columnToColumnInfoMap.get(c)
			if (ctco.isDefined) {
				if (o == null) (c, null) else (c, ctco.get.columnToValue(o))
			} else {
				o match {
					case null =>
						(c, null)
					case pc: T with DeclaredIds[ID] =>
						val ci = pcColumnToColumnInfoMap(c)
						(c, ci.columnToValue(pc))
					case t: T => throw new ExpectedPersistedEntityException(t)
				}
			}
	}

	def toColumnAndValueMap(columns: List[ColumnBase], o: T): Map[ColumnBase, Any] = columns.map {
		c => (c, columnToColumnInfoMap(c).columnToValue(o))
	}.toMap

	def toPCColumnAndValueMap(columns: List[ColumnBase], o: T with DeclaredIds[ID]): Map[ColumnBase, Any] = columns.map {
		c => (c, pcColumnToColumnInfoMap(c).columnToValue(o))
	}.toMap

	def toColumnAliasAndValueMap(columns: List[ColumnBase], o: T): Map[String, Any] = toColumnAndValueMap(columns, o).map(e => (e._1.alias, e._2))

	def toPCColumnAliasAndValueMap(columns: List[ColumnBase], o: T with DeclaredIds[ID]): Map[String, Any] = toPCColumnAndValueMap(columns, o).map(e => (e._1.alias, e._2))

	val selectColumns = simpleTypeColumns ::: manyToOneColumns.map(_.columns).flatten ::: oneToOneColumns.map(_.selfColumns).flatten
	val distinctSelectColumnsForSelect = (selectColumns ::: unusedPKs).distinct

	override def toString = "Table(" + name + ")"
}

case class LinkTable(schema: Option[Schema], name: String, left: List[Column], right: List[Column])
{
	if (schema == null) throw new NullPointerException("databaseSchema should be declared first thing in an entity, for " + name)
	val schemaName = schema.map(_.name)
}
