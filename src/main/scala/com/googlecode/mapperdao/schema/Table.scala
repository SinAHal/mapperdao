package com.googlecode.mapperdao.schema

import com.googlecode.mapperdao.exceptions.ExpectedPersistedEntityException
import com.googlecode.mapperdao.{DeclaredIds, Persisted}

/**
 * mapping tables to entities.
 *
 * This caches a lot of the mappings of an entity so that
 * the rest of the code can quickly access primary keys, foreign mappings etc.
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
	val unusedPKColumnInfos: List[ColumnInfoBase[Any, Any]],
	val versionColumn: Option[ColumnInfo[T, Int]]
	)
{

	val schemaName = schema.map(_.name)
	val columns: List[ColumnBase] = extraColumnInfosPersisted.map(_.column) ::: columnInfosPlain.map(_.column)
	// the primary keys for this table
	val primaryKeys: List[PK] = columns.collect {
		case pk: PK => pk
	}
	val unusedPKs = unusedPKColumnInfos.map {
		case ci: ColumnInfo[_, _] => List(ci.column)
		case ci: ColumnInfoManyToOne[_, _, _] => ci.column.columns
		case ci: ColumnInfoTraversableOneToMany[_, _, _, _] => ci.column.columns
		case ci: ColumnInfoOneToOne[_, _, _] => ci.column.columns
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

	val oneToOneColumns = columns.collect {
		case c: OneToOne[_, _] => c
	}

	val oneToOneReverseColumns = columns.collect {
		case c: OneToOneReverse[_, _] => c
	}

	val oneToManyColumns = columns.collect {
		case c: OneToMany[_, _] => c
	}
	val manyToOneColumns = columns.collect {
		case mto: ManyToOne[_, _] => mto
	}
	val manyToOneColumnsFlattened: List[Column] = columns.collect {
		case ManyToOne(_, columns: List[Column], _) => columns
	}.flatten

	val manyToManyColumns = columns.collect {
		case c: ManyToMany[_, _] => c
	}

	val oneToOneColumnInfos = columnInfosPlain.collect {
		case c: ColumnInfoOneToOne[T, _, _] => c
	}
	val oneToOneReverseColumnInfos = columnInfosPlain.collect {
		case c: ColumnInfoOneToOneReverse[T, _, _] => c
	}

	val oneToManyColumnInfos = columnInfosPlain.collect {
		case c: ColumnInfoTraversableOneToMany[ID, T, _, _] => c
	}
	val manyToOneColumnInfos = columnInfosPlain.collect {
		case c: ColumnInfoManyToOne[T, _, _] => c
	}
	val manyToManyColumnInfos = columnInfosPlain.collect {
		case c: ColumnInfoTraversableManyToMany[T, _, _] => c
	}

	val columnToColumnInfoMap: Map[ColumnBase, ColumnInfoBase[T, _]] = columnInfosPlain.map(ci => (ci.column, ci)).toMap
	val pcColumnToColumnInfoMap: Map[ColumnBase, ColumnInfoBase[T with DeclaredIds[ID], _]] = extraColumnInfosPersisted.map(ci => (ci.column, ci)).toMap

	val manyToManyToColumnInfoMap: Map[ColumnBase, ColumnInfoTraversableManyToMany[T, _, _]] = columnInfosPlain.collect {
		case c: ColumnInfoTraversableManyToMany[T, _, _] => (c.column, c)
	}.toMap

	val oneToManyToColumnInfoMap: Map[ColumnBase, ColumnInfoTraversableOneToMany[ID, T, _, _]] = columnInfosPlain.collect {
		case c: ColumnInfoTraversableOneToMany[ID, T, _, _] => (c.column, c)
	}.toMap

	def toListOfPrimaryKeyValues(o: T): List[Any] = toListOfPrimaryKeyAndValueTuples(o).map(_._2)

	def toListOfPrimaryKeyAndValueTuples(o: T): List[(PK, Any)] = toListOfColumnAndValueTuples(primaryKeys, o)

	def toListOfPrimaryKeySimpleColumnAndValueTuples(o: T): List[(SimpleColumn, Any)] = toListOfColumnAndValueTuples(primaryKeys, o)

	def toListOfUnusedPrimaryKeySimpleColumnAndValueTuples(o: Any): List[(SimpleColumn, Any)] =
		unusedPKColumnInfos.map {
			case ci: ColumnInfo[_, _] =>
				List((ci.column, ci.columnToValue(o)))
			case ci: ColumnInfoManyToOne[_, _, _] =>
				val l = ci.columnToValue(o)
				val fe = ci.column.foreign.entity
				val pks = fe.tpe.table.toListOfPrimaryKeyValues(l)
				ci.column.columns zip pks
			case ci: ColumnInfoTraversableOneToMany[_, _, _, _] =>
				o match {
					case p: Persisted =>
						ci.column.columns map {
							c =>
								(c, p.mapperDaoValuesMap.columnValue[Any](c))
						}
					case _ => Nil
				}
			case ci: ColumnInfoOneToOne[_, _, _] =>
				val l = ci.columnToValue(o)
				val fe = ci.column.foreign.entity
				val pks = fe.tpe.table.toListOfPrimaryKeyValues(l)
				ci.column.columns zip pks

			case ci: ColumnInfoRelationshipBase[_, _, _, _] => Nil
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
					case pc: T@unchecked with DeclaredIds[ID] =>
						val ci = pcColumnToColumnInfoMap(c)
						(c, ci.columnToValue(pc))
					case _ => throw new ExpectedPersistedEntityException(o)
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

