package com.googlecode.mapperdao

import com.googlecode.mapperdao.schema.ColumnInfoRelationshipBase

case class UpdateConfig(
	/**
	 * skip updating related configs, i.e. Set(ProductEntity.attributes)
	 */
	skip: Set[ColumnInfoRelationshipBase[_, _, _, _]] = Set(),

	/**
	 * some updates require deleting related data. This deleteConfig will be used
	 */
	deleteConfig: DeleteConfig = DeleteConfig.default,

	/**
	 * pass these data to the Entity.constructor method
	 */
	data: Option[Any] = None,

	/**
	 * max depth of the update: how deep to go into the entity tree during updates
	 */
	depth: Int = 1000
	)

object UpdateConfig
{
	// use this to avoid creating instances of the default update config
	val default = UpdateConfig()
}