package com.googlecode.mapperdao

/**
 * long simple type
 */
case class LongValue(val value: Long) extends SimpleTypeValue[Long, LongValue] {
	def compareTo(o: LongValue): Int = value.compare(o.value)
}

protected class LongEntityOTM(table: String, fkColumn: String, soleColumn: String)
	extends Entity[Unit, LongValue](table, classOf[LongValue]) {
	val value = column(soleColumn) to (_.value)
	//	declarePrimaryKey(fkColumn) { _ => None }
	declarePrimaryKey(value)

	def constructor(implicit m: ValuesMap) = new LongValue(value) with NoId
}

abstract class LongEntityManyToManyBase[ID](table: String, soleColumn: String) extends Entity[ID, LongValue](table, classOf[LongValue]) {
	val value = column(soleColumn) to (_.value)
}

class LongEntityManyToManyAutoGenerated(table: String, pkColumn: String, soleColumn: String, sequence: Option[String] = None)
	extends LongEntityManyToManyBase[Int](table, soleColumn) {
	val id = key(pkColumn) sequence (sequence) autogenerated (_.id)

	def constructor(implicit m: ValuesMap) = new LongValue(value) with Persisted with SurrogateIntId {
		val id: Int = LongEntityManyToManyAutoGenerated.this.id
	}
}

/**
 * this is for an entity that just stores a long number
 */
object LongEntity {
	def oneToMany(table: String, fkColumn: String, soleColumn: String) = new LongEntityOTM(table, fkColumn, soleColumn)

	def manyToManyAutoGeneratedPK(table: String, pkColumn: String, soleColumn: String, sequence: Option[String] = None) = new LongEntityManyToManyAutoGenerated(table, pkColumn, soleColumn, sequence)
}
