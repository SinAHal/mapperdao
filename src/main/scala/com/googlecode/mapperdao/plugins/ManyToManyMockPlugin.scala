package com.googlecode.mapperdao.plugins

import com.googlecode.mapperdao._

/**
 * @author kostantinos.kougios
 *
 *         11 Dec 2012
 */
class ManyToManyMockPlugin extends SelectMock {
	override def updateMock[ID, T](entity: Entity[ID, T], mods: scala.collection.mutable.Map[String, Any]) {
		mods ++= entity.tpe.table.manyToManyColumns.map(c => (c.alias -> List()))
	}
}