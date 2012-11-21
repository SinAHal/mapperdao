package com.googlecode.mapperdao.ops

import com.googlecode.mapperdao._

/**
 * base for all persist operations
 *
 * @author kostantinos.kougios
 *
 * 21 Nov 2012
 */
trait PersistOp[ID, PC <: DeclaredIds[ID], T] {
	val entity: Entity[ID, PC, T]
	val priority: Int
}