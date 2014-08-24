package com.googlecode.mapperdao.updatephase.persistcmds

import com.googlecode.mapperdao.schema.ColumnInfoManyToOne
import com.googlecode.mapperdao.updatephase.prioritise.Priority
import com.googlecode.mapperdao.{ExternalEntity, ValuesMap}

import scala.language.{existentials, implicitConversions}

/**
 * signals an update which links an entity with an other entity
 *
 * @author kostas.kougios
 *         Date: 1/15/13
 */
case class UpdateExternalManyToOneCmd[FID, FT](
	foreignEntity: ExternalEntity[FID, FT],
	ci: ColumnInfoManyToOne[_, FID, FT],
	newVM: ValuesMap,
	fo: FT
	) extends PersistCmd
{
	def priority = Priority.Low
}