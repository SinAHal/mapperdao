package com.googlecode.mapperdao

/**
 * int simple type
 */
case class IntValue(value: Int) extends SimpleTypeValue[Int, IntValue]
{
	def compareTo(o: IntValue): Int = value.compareTo(o.value)
}

protected class IntEntityOTM(table: String, fkColumn: String, soleColumn: String)
	extends Entity[Unit, NoId, IntValue](table, classOf[IntValue])
{
	val value = column(soleColumn) to (_.value)
	declarePrimaryKey(value)

	def constructor(implicit m: ValuesMap) = new IntValue(value) with NoId
}

abstract class IntEntityManyToManyBase[ID](table: String, soleColumn: String)
	extends Entity[ID, SurrogateIntId, IntValue](table, classOf[IntValue])
{
	val value = column(soleColumn) to (_.value)
}

class IntEntityManyToManyAutoGenerated(table: String, pkColumn: String, soleColumn: String, sequence: Option[String] = None)
	extends IntEntityManyToManyBase[Int](table, soleColumn)
{
	val id = key(pkColumn) sequence (sequence) autogenerated (_.id)

	def constructor(implicit m: ValuesMap) = new IntValue(value) with SurrogateIntId
	{
		val id: Int = IntEntityManyToManyAutoGenerated.this.id
	}
}

/**
 * an entity that holds an integer value only
 */
object IntEntity
{
	def oneToMany(table: String, fkColumn: String, soleColumn: String) = new IntEntityOTM(table, fkColumn, soleColumn)

	def manyToManyAutoGeneratedPK(table: String, pkColumn: String, soleColumn: String, sequence: Option[String] = None) = new IntEntityManyToManyAutoGenerated(table, pkColumn, soleColumn, sequence)
}