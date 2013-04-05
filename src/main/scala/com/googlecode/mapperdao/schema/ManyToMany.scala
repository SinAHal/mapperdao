package com.googlecode.mapperdao.schema

import com.googlecode.mapperdao.{TypeRef, Persisted, Entity}

case class ManyToMany[FID, F](
	entity: Entity[_, _ <: Persisted, _],
	linkTable: LinkTable,
	foreign: TypeRef[FID, F]
	)
	extends ColumnRelationshipBase[FID, F]
{
	def alias = foreign.alias

	override def columns: List[Column] = Nil

	override def toString = "manytomany(%s) foreignkey (%s)".format(foreign.entity.getClass.getSimpleName, columns.map(_.name).mkString(","))
}
