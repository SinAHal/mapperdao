package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * @author kostantinos.kougios
 *
 * 5 Sep 2011
 */
@RunWith(classOf[JUnitRunner])
class ManyToOneAutoGeneratedSuite extends FunSuite with ShouldMatchers {
	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(PersonEntity, CompanyEntity, HouseEntity))

	test("select with skip") {
		createTables

		val company = Company("Coders limited")
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company, house)

		val inserted = mapperDao.insert(PersonEntity, person)
		mapperDao.select(SelectConfig(skip = Set(PersonEntity.company)), PersonEntity, inserted.id).get should be === Person("Kostas", null, house)
		mapperDao.select(SelectConfig(skip = Set(PersonEntity.lives)), PersonEntity, inserted.id).get should be === Person("Kostas", company, null)
	}

	test("update to null both FK") {
		createTables

		import mapperDao._
		val company1 = insert(CompanyEntity, Company("Coders limited"))
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company1, house)

		val inserted = insert(PersonEntity, person)
		inserted should be === person

		val modified = Person("changed", null, null)
		val updated = update(PersonEntity, inserted, modified)
		updated should be === modified

		val selected = select(PersonEntity, inserted.id).get
		selected should be === updated

		mapperDao.delete(PersonEntity, selected)
		mapperDao.select(PersonEntity, selected.id) should be(None)
	}

	test("update to null") {
		createTables

		import mapperDao._
		val company1 = insert(CompanyEntity, Company("Coders limited"))
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company1, house)

		val inserted = insert(PersonEntity, person)
		inserted should be === person

		val modified = Person("changed", null, inserted.lives)
		val updated = update(PersonEntity, inserted, modified)
		updated should be === modified

		val selected = select(PersonEntity, updated.id).get
		selected should be === updated

		mapperDao.delete(PersonEntity, selected)
		mapperDao.select(PersonEntity, selected.id) should be(None)
	}

	test("insert") {
		createTables

		val company = Company("Coders limited")
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company, house)

		val inserted = mapperDao.insert(PersonEntity, person)
		inserted should be === person
	}

	test("insert with existing foreign entity") {
		createTables

		import mapperDao._
		val company = insert(CompanyEntity, Company("Coders limited"))
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company, house)

		val inserted = insert(PersonEntity, person)
		inserted should be === person

		val selected = select(PersonEntity, inserted.id).get
		selected should be === inserted

		mapperDao.delete(PersonEntity, inserted)
		mapperDao.select(PersonEntity, inserted.id) should be(None)
	}

	test("select") {
		createTables

		import mapperDao._
		val company = Company("Coders limited")
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company, house)

		val inserted = insert(PersonEntity, person)

		val selected = select(PersonEntity, inserted.id).get
		selected should be === inserted

		mapperDao.delete(PersonEntity, inserted)
		mapperDao.select(PersonEntity, inserted.id) should be(None)
	}

	test("select with null FK") {
		createTables

		import mapperDao._
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", null, house)

		val inserted = insert(PersonEntity, person)

		val selected = select(PersonEntity, inserted.id).get
		selected should be === inserted

		mapperDao.delete(PersonEntity, inserted)
		mapperDao.select(PersonEntity, inserted.id) should be(None)
	}

	test("update") {
		createTables

		import mapperDao._
		val company1 = insert(CompanyEntity, Company("Coders limited"))
		val company2 = insert(CompanyEntity, Company("Scala Inc"))
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company1, house)

		val inserted = insert(PersonEntity, person)
		inserted should be === person

		val modified = Person("changed", company2, inserted.lives)
		val updated = update(PersonEntity, inserted, modified)
		updated should be === modified

		val selected = select(PersonEntity, updated.id).get
		selected should be === updated

		mapperDao.delete(PersonEntity, selected)
		mapperDao.select(PersonEntity, selected.id) should be(None)
	}

	def createTables =
		{
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update("ddl")
			Setup.database match {
				case "oracle" =>
					Setup.createSeq(jdbc, "CompanySeq")
					Setup.createSeq(jdbc, "HouseSeq")
					Setup.createSeq(jdbc, "PersonSeq")
				case _ =>
			}
		}

	case class Person(val name: String, val company: Company, val lives: House)
	case class Company(val name: String)
	case class House(val address: String)

	object PersonEntity extends Entity[IntId, Person] {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("PersonSeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)
		val company = manytoone(CompanyEntity) to (_.company)
		val lives = manytoone(HouseEntity) to (_.lives)

		def constructor(implicit m) = new Person(name, company, lives) with IntId with Persisted {
			val id: Int = PersonEntity.id
		}
	}

	object CompanyEntity extends Entity[IntId, Company] {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("CompanySeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)

		def constructor(implicit m) = new Company(name) with IntId with Persisted {
			val id: Int = CompanyEntity.id
		}
	}

	object HouseEntity extends Entity[IntId, House] {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("HouseSeq")
			case _ => None
		}) autogenerated (_.id)
		val address = column("address") to (_.address)
		def constructor(implicit m) = new House(address) with IntId with Persisted {
			val id: Int = HouseEntity.id
		}
	}
}