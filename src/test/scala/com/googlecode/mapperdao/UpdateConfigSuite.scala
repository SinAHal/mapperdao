package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import com.googlecode.mapperdao.jdbc.Jdbc
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * @author kostantinos.kougios
 *
 *         18 Oct 2011
 */
@RunWith(classOf[JUnitRunner])
class UpdateConfigSuite extends FunSuite with ShouldMatchers
{

	test("one-to-many update.deleteConfig") {
		import UpdateConfigSuiteOneToManyDecl._
		val (jdbc, mapperDao, _) = Setup.setupMapperDao(TypeRegistry(FloorEntity, HouseEntity, PersonEntity))
		prepareDb(jdbc, "OneToManyDecl")

		val inserted = mapperDao.insert(PersonEntity, Person(1, "kostas", Set(House(10, Set(Floor(5, "floor5"), Floor(6, "floor6"))), House(11, Set(Floor(7, "floor7"), Floor(8, "floor8"))))))
		mapperDao.update(UpdateConfig(deleteConfig = DeleteConfig(propagate = true)), PersonEntity, inserted, Person(inserted.id, inserted.name, inserted.owns.filterNot(_.id == 11)))

		jdbc.queryForInt("select count(*) from Floor") should be === 2
	}

	def prepareDb(jdbc: Jdbc, tableCreationScript: String): Unit = {
		Setup.dropAllTables(jdbc)
		val queries = Setup.queries(this, jdbc)
		queries.update(tableCreationScript)
	}
}

object UpdateConfigSuiteOneToManyDecl
{

	case class Person(val id: Int, var name: String, owns: Set[House])

	case class House(val id: Int, val floors: Set[Floor])

	case class Floor(val id: Int, val description: String)

	object FloorEntity extends Entity[Int, Floor]
	{
		type Stored = NaturalIntId
		val id = key("id") to (_.id)
		val description = column("description") to (_.description)

		def constructor(implicit m) = new Floor(id, description) with Stored
	}

	object HouseEntity extends Entity[Int, House]
	{
		type Stored = NaturalIntId
		val id = key("id") to (_.id)
		val floors = onetomany(FloorEntity) to (_.floors)

		def constructor(implicit m) = new House(id, floors) with Stored
	}

	object PersonEntity extends Entity[Int, Person]
	{
		type Stored = NaturalIntId
		val id = key("id") to (_.id)
		val name = column("name") to (_.name)
		val houses = onetomany(HouseEntity) to (_.owns)

		def constructor(implicit m) = new Person(id, name, houses) with Stored
	}

}
