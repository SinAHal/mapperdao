package com.googlecode.mapperdao.state.persisted

import com.googlecode.mapperdao._

/**
 * after persisting to the storage, all commands are converted to persisted nodes
 *
 * @author kostantinos.kougios
 *
 *         Dec 8, 2012
 */
trait PersistedNode[ID, T] {
	val entity: Entity[ID, DeclaredIds[ID], T]
	val mainEntity: Boolean

	def identity: Int
}
