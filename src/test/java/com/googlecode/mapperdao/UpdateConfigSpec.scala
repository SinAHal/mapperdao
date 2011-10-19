package com.googlecode.mapperdao
import org.specs2.mutable.SpecificationWithJUnit
import com.googlecode.mapperdao.jdbc.Setup
import com.googlecode.mapperdao.jdbc.Jdbc
import com.googlecode.mapperdao.jdbc.Queries

/**
 * @author kostantinos.kougios
 *
 * 18 Oct 2011
 */
class UpdateConfigSpec extends SpecificationWithJUnit {

	"one-to-many update.deleteConfig" in {
		import UpdateConfigSpecOneToManyDecl._
		val (jdbc, driver, mapperDao) = Setup.setupMapperDao(TypeRegistry(FloorEntity, HouseEntity, PersonEntity))
		prepareDb(jdbc, "OneToManyDecl")

		val inserted = mapperDao.insert(PersonEntity, Person(1, "kostas", Set(House(10, Set(Floor(5, "nice floor"), Floor(6, "top floor"))))))
		mapperDao.update(PersonEntity, inserted, Person(1, inserted.name, inserted.owns.map(h => House(10, h.floors.filterNot(_.id == 5)))))

		jdbc.queryForInt("select count(*) from Floor") must_== 1
	}

	def prepareDb(jdbc: Jdbc, tableCreationScript: String): Unit = {
		Setup.dropAllTables(jdbc)
		val queries = Setup.queries(this, jdbc)
		queries.update(tableCreationScript)
	}
}

object UpdateConfigSpecOneToManyDecl {
	case class Person(val id: Int, var name: String, owns: Set[House])
	case class House(val id: Int, val floors: Set[Floor])
	case class Floor(val id: Int, val description: String)

	object FloorEntity extends SimpleEntity(classOf[Floor]) {
		val id = intPK("id", _.id)
		val description = string("description", _.description)

		def constructor(implicit m: ValuesMap) = new Floor(id, description) with Persisted
	}

	object HouseEntity extends SimpleEntity(classOf[House]) {
		val id = intPK("id", _.id)
		val floors = oneToMany(classOf[Floor], _.floors)

		def constructor(implicit m: ValuesMap) = new House(id, floors) with Persisted
	}

	object PersonEntity extends SimpleEntity(classOf[Person]) {
		val id = intPK("id", _.id)
		val name = string("name", _.name)
		val houses = oneToMany(classOf[House], _.owns)
		def constructor(implicit m: ValuesMap) = new Person(id, name, houses) with Persisted
	}
}
