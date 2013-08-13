package com.googlecode.mapperdao.updatephase.persistcmds

import com.googlecode.mapperdao.updatephase.prioritise.Priority

/**
 * base for all persist operations
 *
 * @author kostantinos.kougios
 *
 *         21 Nov 2012
 */
trait PersistCmd
{
	def priority: Priority
}
