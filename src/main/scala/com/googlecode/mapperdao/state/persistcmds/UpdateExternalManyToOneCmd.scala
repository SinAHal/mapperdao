package com.googlecode.mapperdao.state.persistcmds

import com.googlecode.mapperdao.ExternalEntity
import com.googlecode.mapperdao.state.prioritise.Priority

/**
 * @author: kostas.kougios
 *          Date: 1/15/13
 */
case class UpdateExternalManyToOneCmd[FID, FT](
	foreignEntity: ExternalEntity[FID, FT],
	fo: FT
	) extends PersistCmd {
	def priority = Priority.Low
}