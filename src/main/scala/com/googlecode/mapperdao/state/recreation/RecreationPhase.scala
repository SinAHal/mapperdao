package com.googlecode.mapperdao.state.recreation

import com.googlecode.mapperdao._
import state.persisted.PersistedNode
import com.googlecode.mapperdao.schema._
import com.googlecode.mapperdao.state.persisted.ExternalEntityPersistedNode
import com.googlecode.mapperdao.state.persisted.EntityPersistedNode
import com.googlecode.mapperdao.schema.ColumnInfoTraversableOneToMany
import com.googlecode.mapperdao.schema.ColumnInfoOneToOne
import com.googlecode.mapperdao.schema.ColumnInfoTraversableManyToMany
import com.googlecode.mapperdao.schema.ColumnInfoManyToOne
import com.googlecode.mapperdao.internal.UpdateEntityMap
import java.util

/**
 * during recreation phase, persisted objects are re-created with Stored type mixed in the
 * new instances.
 *
 * @author kostantinos.kougios
 *
 *         11 Dec 2012
 */
class RecreationPhase(
	updateConfig: UpdateConfig,
	mockFactory: MockFactory,
	typeManager: TypeManager,
	entityMap: UpdateEntityMap,
	nodes: List[PersistedNode[_, _]]
	)
{

	private val byIdentity: util.IdentityHashMap[Any, PersistedNode[_, _]] = {
		val m = new util.IdentityHashMap[Any, PersistedNode[_, _]]
		nodes.foreach {
			node =>
				val o = node.o
				if (o == null)
					throw new IllegalStateException("unexpected error, o is null")
				m.put(o, node)
		}
		m
	}

	def execute = recreate(updateConfig, nodes.filter(_.mainEntity)).toList

	private def recreate(updateConfig: UpdateConfig, nodes: Traversable[PersistedNode[_, _]]): Traversable[Any] =
		nodes.map {
			node =>
				entityMap.get[Any](node.o).getOrElse {

					node match {
						case EntityPersistedNode(tpe, oldVM, newVM, _) =>
							val table = tpe.table

							val modified = newVM.toMap

							// create a mock
							val mockO = mockFactory.createMock(updateConfig.data, tpe, modified)
							entityMap.put(node.o, mockO)

							val related = if (newVM.mock)
								Nil
							else table.relationshipColumnInfos(updateConfig.skip).map {
								case ColumnInfoTraversableManyToMany(column, _, _) =>
									val mtm = newVM.manyToMany(column)
									val relatedNodes = toNodes(mtm)
									(
										column.alias,
										recreate(updateConfig, relatedNodes)
										)
								case ColumnInfoManyToOne(column, _, _) =>
									val mto = newVM.manyToOne(column)
									if (mto == null) {
										(column.alias, null)
									} else {
										val relatedNodes = toNode(mto) :: Nil
										(column.alias, recreate(updateConfig, relatedNodes).head)
									}
								case ColumnInfoTraversableOneToMany(column, _, _, _) =>
									val otm = newVM.oneToMany(column)
									val relatedNodes = toNodes(otm)

									(
										column.alias,
										recreate(updateConfig, relatedNodes)
										)
								case ColumnInfoOneToOne(column, _) =>
									val oto = newVM.oneToOne(column)
									if (oto == null) {
										(column.alias, null)
									} else {
										val relatedNodes = toNode(oto) :: Nil
										(column.alias, recreate(updateConfig, relatedNodes).head)
									}
								case ColumnInfoOneToOneReverse(column, _, _) =>
									val oto = newVM.oneToOneReverse(column)
									if (oto == null) {
										(column.alias, null)
									} else {
										val relatedNodes = toNode(oto) :: Nil
										(column.alias, recreate(updateConfig, relatedNodes).head)
									}
							}

							val finalMods = modified ++ related
							val finalVM = ValuesMap.fromMap(null, finalMods)
							val newE = tpe.constructor(updateConfig.data, finalVM)
							finalVM.o = newE
							// re-put the actual
							entityMap.put(finalVM, newE)
							newE

						case ExternalEntityPersistedNode(entity, o) =>
							o
					}
				}
		}

	private def toNode(a: Any) = {
		val v = byIdentity.get(a)
		if (v == null) throw new IllegalStateException("internal error, null not expected for " + a)
		v
	}

	private def toNodes(l: Traversable[Any]) = l.map(toNode(_))
}