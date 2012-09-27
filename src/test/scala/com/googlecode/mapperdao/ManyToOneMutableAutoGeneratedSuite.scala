package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * @author kostantinos.kougios
 *
 * 6 Sep 2011
 */
@RunWith(classOf[JUnitRunner])
class ManyToOneMutableAutoGeneratedSuite extends FunSuite with ShouldMatchers {
	val (jdbc, mapperDao: MapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(PersonEntity, CompanyEntity, HouseEntity))

	import mapperDao._

	test("update to null both FK") {
		createTables

		val company1 = insert(CompanyEntity, Company("Coders limited"))
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company1, house)

		val inserted = insert(PersonEntity, person)
		inserted should be === person

		inserted.name = "changed"
		inserted.company = null
		inserted.lives = null
		val updated = update(PersonEntity, inserted)
		updated should be === inserted

		val selected = select(PersonEntity, inserted.id).get
		selected should be === updated

		mapperDao.delete(PersonEntity, selected)
		mapperDao.select(PersonEntity, selected.id) should be(None)
	}

	test("update to null") {
		createTables

		val company1 = insert(CompanyEntity, Company("Coders limited"))
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company1, house)

		val inserted = insert(PersonEntity, person)
		inserted should be === person

		inserted.name = "changed"
		inserted.company = null

		val updated = update(PersonEntity, inserted)
		updated should be === inserted

		val selected = select(PersonEntity, updated.id).get
		selected should be === updated

		mapperDao.delete(PersonEntity, selected)
		mapperDao.select(PersonEntity, selected.id) should be(None)
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
		inserted.company = company2
		val updated = update(PersonEntity, inserted)
		updated should be === inserted

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

	case class Person(var name: String, var company: Company, var lives: House)
	case class Company(var name: String)
	case class House(var address: String)

	object PersonEntity extends Entity[SurrogateIntId, Person] {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("PersonSeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)
		val company = manytoone(CompanyEntity) to (_.company)
		val lives = manytoone(HouseEntity) to (_.lives)

		def constructor(implicit m) = new Person(name, company, lives) with SurrogateIntId {
			val id: Int = PersonEntity.id
		}
	}

	object CompanyEntity extends Entity[SurrogateIntId, Company] {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("CompanySeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)

		def constructor(implicit m) = new Company(name) with SurrogateIntId {
			val id: Int = CompanyEntity.id
		}
	}

	object HouseEntity extends Entity[SurrogateIntId, House] {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("HouseSeq")
			case _ => None
		}) autogenerated (_.id)
		val address = column("address") to (_.address)
		def constructor(implicit m) = new House(address) with SurrogateIntId {
			val id: Int = HouseEntity.id
		}
	}
}