package com.googlecode.mapperdao

/**
 * float simple type
 */
case class FloatValue(val value: Float) extends SimpleTypeValue[Float, FloatValue] {
	def compareTo(o: FloatValue): Int = value.compare(o.value)
}

protected class FloatEntityOTM(table: String, fkColumn: String, soleColumn: String)
	extends Entity[Unit, FloatValue](table, classOf[FloatValue]) {
	val value = column(soleColumn) to (_.value)
	//	declarePrimaryKey(fkColumn) { _ => None }
	declarePrimaryKey(value)

	def constructor(implicit m: ValuesMap) = new FloatValue(value) with NoId
}

abstract class FloatEntityManyToManyBase[ID](table: String, soleColumn: String)
	extends Entity[ID, FloatValue](table, classOf[FloatValue]) {
	val value = column(soleColumn) to (_.value)
}

class FloatEntityManyToManyAutoGenerated(table: String, pkColumn: String, soleColumn: String, sequence: Option[String] = None)
	extends FloatEntityManyToManyBase[Int](table, soleColumn) {
	val id = key(pkColumn) sequence (sequence) autogenerated (_.id)

	def constructor(implicit m: ValuesMap) = new FloatValue(value) with Persisted with SurrogateIntId {
		val id: Int = FloatEntityManyToManyAutoGenerated.this.id
	}
}

object FloatEntity {
	def oneToMany(table: String, fkColumn: String, soleColumn: String) = new FloatEntityOTM(table, fkColumn, soleColumn)

	def manyToManyAutoGeneratedPK(table: String, pkColumn: String, soleColumn: String, sequence: Option[String] = None) = new FloatEntityManyToManyAutoGenerated(table, pkColumn, soleColumn, sequence)
}
