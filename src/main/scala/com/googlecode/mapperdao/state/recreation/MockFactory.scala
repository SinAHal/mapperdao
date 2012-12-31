package com.googlecode.mapperdao.state.recreation

import com.googlecode.mapperdao._
import com.googlecode.mapperdao.plugins._

/**
 * @author kostantinos.kougios
 *
 *         11 Dec 2012
 */
class MockFactory(typeManager: TypeManager) {
	private val mockPlugins: List[SelectMock] = List(
		new OneToManyMockPlugin,
		new ManyToManyMockPlugin,
		new ManyToOneMockPlugin,
		new OneToOneMockPlugin
	)

	def createMock[ID, T](
		data: Option[Any],
		entity: Entity[ID, T],
		mods: Map[String, Any]
	): T with DeclaredIds[ID] = {
		val mockMods = new scala.collection.mutable.HashMap[String, Any] ++ mods
		mockPlugins.foreach {
			_.updateMock(entity, mockMods)
		}
		val tpe = entity.tpe
		val vm = ValuesMap.fromMap(1, mockMods)
		val preMock = tpe.constructor(data, vm)
		val mock = tpe.constructor(data, ValuesMap.fromEntity(typeManager, entity, preMock))
		// mark it as mock
		mock.mapperDaoMock = true
		mock.mapperDaoValuesMap.identity = System.identityHashCode(mock)
		mock
	}

}