package com.googlecode.mapperdao.state.persisted

import com.googlecode.mapperdao.SimpleColumn
import com.googlecode.mapperdao.ColumnInfoRelationshipBase
import com.googlecode.mapperdao.DeclaredIds
import org.springframework.jdbc.core.SqlParameterValue
import com.googlecode.mapperdao.Entity

/**
 * after persisting to the storage, all commands are converted to persisted nodes
 *
 * @author kostantinos.kougios
 *
 * Dec 8, 2012
 */
case class PersistedNode[ID, PC <: DeclaredIds[ID], T](
		sql: String,
		values: List[SqlParameterValue],
		entity: Entity[ID, PC, T],
		o: T,
		children: List[(ColumnInfoRelationshipBase[_, _, _, _, _], PersistedNode[_, _, _])],
		var keys: List[(SimpleColumn, Any)],
		var newO: Option[T with PC]) {

	def keysToMap = keys.map {
		case (c, v) => (c.name, v)
	}.toMap
}
