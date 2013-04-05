package com.googlecode.mapperdao.jdbc

import java.util.concurrent.ConcurrentHashMap
import com.googlecode.mapperdao.internal.MultiThreadedQueryEntityMapImpl
import com.googlecode.mapperdao._

/**
 * runs queries using multiple threads via parallel scala collections
 *
 * @author kostantinos.kougios
 *
 *         6 May 2012
 */
private[mapperdao] class ParQueryRunStrategy extends QueryRunStrategy
{

	override def run[ID, T](
		mapperDao: MapperDaoImpl,
		entity: Entity[ID, Persisted, T],
		queryConfig: QueryConfig,
		lm: List[DatabaseValues]
		) = {
		// a global cache for fully loaded entities
		val globalL1 = new ConcurrentHashMap[List[Any], Option[_]]
		val selectConfig = SelectConfig.from(queryConfig)

		// group the query results and par-map them to entities.
		// we also need to maintain the order of retrieved results.
		val lmc = lm.grouped(queryConfig.multi.inGroupsOf)
			.zipWithIndex
			.toList
			.par
			.map {
			case (l, idx) =>
				val entityMap = new MultiThreadedQueryEntityMapImpl(globalL1)
				val v = mapperDao.toEntities(l, entity, selectConfig, entityMap)
				(v, idx)
		}
			.toList
			.sortWith((t1, t2) => t1._2 < t2._2)
			.map(_._1)
		lmc.flatten
	}
}