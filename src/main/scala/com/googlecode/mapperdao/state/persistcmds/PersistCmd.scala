package com.googlecode.mapperdao.state.persistcmds

import com.googlecode.mapperdao._

/**
 * base for all persist operations
 *
 * @author kostantinos.kougios
 *
 * 21 Nov 2012
 */
trait PersistCmd[ID, T] {
	val entity: Entity[ID, DeclaredIds[ID], T]
	val commands: List[PersistCmd[_, _]]
}
