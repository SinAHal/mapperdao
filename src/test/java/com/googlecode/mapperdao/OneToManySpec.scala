package com.googlecode.mapperdao

import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.matcher.MatchResult
import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

/**
 * this spec is self contained, all entities, mapping are contained in this class
 *
 * @author kostantinos.kougios
 *
 * 12 Jul 2011
 */
@RunWith(classOf[JUnitRunner])
class OneToManySpec extends SpecificationWithJUnit {

	import OneToManySpec._

	val (jdbc, driver, mapperDao) = Setup.setupMapperDao(TypeRegistry(JobPositionEntity, HouseEntity, PersonEntity))

	"updating id of many entity" in {
		createTables

		val jp1 = new JobPosition(3, "C++ Developer", 10)
		val jp2 = new JobPosition(5, "Scala Developer", 10)
		val jp3 = new JobPosition(7, "Java Developer", 10)
		val person = new Person(3, "Kostas", "K", Set(House(1, "London"), House(2, "Rhodes")), 16, List(jp1, jp2, jp3))
		val inserted = mapperDao.insert(PersonEntity, person)
		val house = inserted.owns.find(_.id == 1).get
		mapperDao.update(HouseEntity, house, House(5, "London"))
		mapperDao.select(PersonEntity, 3).get must_== Person(3, "Kostas", "K", Set(House(5, "London"), House(2, "Rhodes")), 16, List(jp1, jp2, jp3))
	}

	if (Setup.database != "derby") {
		"updating id of primary entity" in {
			createTables

			val jp1 = new JobPosition(3, "C++ Developer", 10)
			val jp2 = new JobPosition(5, "Scala Developer", 10)
			val jp3 = new JobPosition(7, "Java Developer", 10)
			val person = new Person(3, "Kostas", "K", Set(House(1, "London"), House(2, "Rhodes")), 16, List(jp1, jp2, jp3))
			val inserted = mapperDao.insert(PersonEntity, person)
			val updated = mapperDao.update(PersonEntity, inserted, Person(8, "Kostas", "K", inserted.owns, 16, inserted.positions))
			updated must_== Person(8, "Kostas", "K", person.owns, 16, person.positions)
			mapperDao.select(PersonEntity, 8).get must_== updated
			mapperDao.select(PersonEntity, 3) must beNone
		}
	}

	"updating items (immutable)" in {
		createTables

		val jp1 = new JobPosition(3, "C++ Developer", 10)
		val jp2 = new JobPosition(5, "Scala Developer", 10)
		val jp3 = new JobPosition(7, "Java Developer", 10)
		val jp4 = new JobPosition(8, "Web Designer", 10)
		val jp5 = new JobPosition(1, "Graphics Designer", 10)
		val person = Person(3, "Kostas", "K", Set(House(1, "London"), House(2, "Rhodes")), 16, List(jp1, jp2, jp3))
		val inserted = mapperDao.insert(PersonEntity, person)

		var updated: Person = inserted
		def doUpdate(from: Person, to: Person) =
			{
				updated = mapperDao.update(PersonEntity, from, to)
				updated must_== to
				mapperDao.select(PersonEntity, 3).get must_== updated
				mapperDao.select(PersonEntity, 3).get must_== to
			}
		doUpdate(updated, new Person(3, "Changed", "K", updated.owns, 18, updated.positions.filterNot(_ == jp1)))
		doUpdate(updated, new Person(3, "Changed Again", "Surname changed too", updated.owns.filter(_.address == "London"), 18, jp5 :: updated.positions.filterNot(jp ⇒ jp == jp1 || jp == jp3)))

		mapperDao.delete(PersonEntity, updated)
		mapperDao.select(PersonEntity, updated.id) must beNone
	}

	"updating items (mutable)" in {
		createTables

		val jp1 = new JobPosition(3, "C++ Developer", 10)
		val jp2 = new JobPosition(5, "Scala Developer", 10)
		val jp3 = new JobPosition(7, "Java Developer", 10)
		val person = new Person(3, "Kostas", "K", Set(House(1, "London"), House(2, "Rhodes")), 16, List(jp1, jp2, jp3))
		val inserted = mapperDao.insert(PersonEntity, person)

		inserted.positions.foreach(_.name = "changed")
		inserted.positions.foreach(_.rank = 5)
		val updated = mapperDao.update(PersonEntity, inserted)
		updated must_== inserted

		val loaded = mapperDao.select(PersonEntity, 3).get
		loaded must_== updated

		mapperDao.delete(PersonEntity, updated)
		mapperDao.select(PersonEntity, updated.id) must beNone
	}

	"removing items" in {
		createTables

		val jp1 = new JobPosition(3, "C++ Developer", 10)
		val jp2 = new JobPosition(5, "Scala Developer", 10)
		val jp3 = new JobPosition(7, "Java Developer", 10)
		val person = new Person(3, "Kostas", "K", Set(House(1, "London"), House(2, "Rhodes")), 16, List(jp1, jp2, jp3))
		val inserted = mapperDao.insert(PersonEntity, person)

		inserted.positions = inserted.positions.filterNot(jp ⇒ jp == jp1 || jp == jp3)
		val updated = mapperDao.update(PersonEntity, inserted)
		updated must_== inserted

		val loaded = mapperDao.select(PersonEntity, 3).get
		loaded must_== updated

		mapperDao.delete(PersonEntity, updated)
		mapperDao.select(PersonEntity, updated.id) must beNone
	}

	"adding items" in {
		createTables

		val person = new Person(3, "Kostas", "K", Set(House(1, "London"), House(2, "Rhodes")), 16, List(new JobPosition(5, "Scala Developer", 10), new JobPosition(7, "Java Developer", 10)))
		mapperDao.insert(PersonEntity, person)

		val loaded = mapperDao.select(PersonEntity, 3).get

		// add more elements to the collection
		loaded.positions = new JobPosition(1, "C++ Developer", 8) :: loaded.positions
		loaded.positions = new JobPosition(0, "Groovy Developer", 5) :: loaded.positions
		val updatedPositions = mapperDao.update(PersonEntity, loaded)
		updatedPositions must_== loaded

		val updatedReloaded = mapperDao.select(PersonEntity, 3).get
		updatedReloaded must_== updatedPositions

		mapperDao.delete(PersonEntity, updatedReloaded)
		mapperDao.select(PersonEntity, updatedReloaded.id) must beNone
	}

	"CRUD (multi purpose test)" in {
		createTables

		val person = new Person(3, "Kostas", "K", Set(House(1, "London"), House(2, "Rhodes")), 16, List(new JobPosition(5, "Scala Developer", 10), new JobPosition(7, "Java Developer", 10)))
		mapperDao.insert(PersonEntity, person)

		val loaded = mapperDao.select(PersonEntity, 3).get
		loaded must_== person

		// update

		loaded.name = "Changed"
		loaded.age = 24
		loaded.positions.head.name = "Java/Scala Developer"
		loaded.positions.head.rank = 123
		val updated = mapperDao.update(PersonEntity, loaded)
		updated must_== loaded

		val reloaded = mapperDao.select(PersonEntity, 3).get
		reloaded must_== loaded

		// add more elements to the collection
		reloaded.positions = new JobPosition(1, "C++ Developer", 8) :: reloaded.positions
		val updatedPositions = mapperDao.update(PersonEntity, reloaded)
		updatedPositions must_== reloaded

		val updatedReloaded = mapperDao.select(PersonEntity, 3).get
		updatedReloaded must_== updatedPositions

		// remove elements from the collection
		updatedReloaded.positions = updatedReloaded.positions.filterNot(_ == updatedReloaded.positions(1))
		val removed = mapperDao.update(PersonEntity, updatedReloaded)
		removed must_== updatedReloaded

		val removedReloaded = mapperDao.select(PersonEntity, 3).get
		removedReloaded must_== removed

		// remove them all
		removedReloaded.positions = List()
		mapperDao.update(PersonEntity, removedReloaded) must_== removedReloaded
		mapperDao.select(PersonEntity, 3).get must_== removedReloaded
	}

	def createTables {
		Setup.dropAllTables(jdbc)
		Setup.queries(this, jdbc).update("ddl")
	}
}

object OneToManySpec {
	/**
	 * ============================================================================================================
	 * the entities
	 * ============================================================================================================
	 */
	/**
	 * the only reason this is a case class, is to ease testing. There is no requirement
	 * for persisted classes to follow any convention.
	 *
	 * Also the only reason for this class to be mutable is for testing. In a real application
	 * it could be immutable.
	 */
	case class JobPosition(val id: Int, var name: String, var rank: Int) {
		// this can have any arbitrary methods, no problem!
		def whatRank = rank
		// also any non persisted fields, no prob! It's up to the mapper which fields will be used
		val whatever = 5
	}

	/**
	 * the only reason this is a case class, is to ease testing. There is no requirement
	 * for persisted classes to follow any convention
	 *
	 * Also the only reason for this class to be mutable is for testing. In a real application
	 * it could be immutable.
	 */
	case class Person(val id: Int, var name: String, val surname: String, owns: Set[House], var age: Int, var positions: List[JobPosition])

	case class House(val id: Int, val address: String)

	/**
	 * ============================================================================================================
	 * Mapping for JobPosition class
	 * ============================================================================================================
	 */
	object JobPositionEntity extends SimpleEntity(classOf[JobPosition]) {

		// now a description of the table and it's columns follows.
		// each column is followed by a function JobPosition=>T, that
		// returns the value of the property for that column.
		val id = key("id") to (_.id) // this is the primary key
		val name = column("name") to (_.name) // _.name : JobPosition => Any . Function that maps the column to the value of the object
		val rank = column("rank") to (_.rank)

		def constructor(implicit m: ValuesMap) = new JobPosition(id, name, rank) with Persisted
	}

	object HouseEntity extends SimpleEntity(classOf[House]) {
		val id = key("id") to (_.id)
		val address = column("address") to (_.address)

		def constructor(implicit m: ValuesMap) = new House(id, address) with Persisted
	}
	object PersonEntity extends SimpleEntity(classOf[Person]) {
		val id = key("id") to (_.id)
		val name = column("name") to (_.name)
		val surname = column("surname") to (_.surname)
		val houses = onetomany(HouseEntity) to (_.owns)
		val age = column("age") to (_.age)
		/**
		 * a traversable one-to-many relationship with JobPositions.
		 * The type of the relationship is classOf[JobPosition] and the alias
		 * for retrieving the Traversable is jobPositionsAlias. This is used above, when
		 * creating Person: new Person(....,m.toList("jobPositionsAlias")) .
		 * JobPositions table has a person_id foreign key which references Person table.
		 */
		val jobPositions = onetomany(JobPositionEntity) to (_.positions)

		def constructor(implicit m: ValuesMap) = new Person(id, name, surname, houses, age, m(jobPositions).toList.sortWith(_.id < _.id)) with Persisted
	}
}