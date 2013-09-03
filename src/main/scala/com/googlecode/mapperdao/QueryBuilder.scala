package com.googlecode.mapperdao

/**
 * @author: kostas.kougios
 *          Date: 30/05/13
 */
trait QueryBuilder[ID, PC <: Persisted, T]
{
	private[mapperdao] def entity: EntityBase[ID, T]

	private[mapperdao] def joins: List[Any]
}
