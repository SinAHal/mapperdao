package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * @author kostantinos.kougios
 *
 * 11 Sep 2011
 */
@RunWith(classOf[JUnitRunner])
class ManyToOneAndOneToManyCyclicAutoGeneratedSuite extends FunSuite with ShouldMatchers {
	import ManyToOneAndOneToManyCyclicAutoGeneratedSpec._
	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(PersonEntity, CompanyEntity))

	test("insert") {
		createTables
		import mapperDao._

		val company = insert(CompanyEntity, Company("Coders Ltd", List()))
		val person = Person("Coder1", company)
		insert(PersonEntity, person) should be === person
	}

	test("select") {
		createTables
		import mapperDao._

		val company = insert(CompanyEntity, Company("Coders Ltd", List()))
		val inserted = insert(PersonEntity, Person("Coder1", company))

		// the person in the list is a mock object due to the cyclic dependency, and company is null
		select(PersonEntity, inserted.id).get should be === Person("Coder1", Company("Coders Ltd", List(Person("Coder1", null))))
	}

	test("update") {
		createTables
		import mapperDao._

		val company = insert(CompanyEntity, Company("Coders Ltd", List()))
		val inserted = insert(PersonEntity, Person("Coder1", company))

		val selected = select(PersonEntity, inserted.id).get

		val updated = update(PersonEntity, selected, Person("Coder1-changed", company))
		updated should be === Person("Coder1-changed", Company("Coders Ltd", List()))

		select(CompanyEntity, company.id).get should be === Company("Coders Ltd", List(Person("Coder1-changed", Company("Coders Ltd", List()))))
	}

	def createTables =
		{
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update("ddl")
			Setup.database match {
				case "oracle" =>
					Setup.createSeq(jdbc, "CompanySeq")
					Setup.createSeq(jdbc, "PersonSeq")
				case _ =>
			}
		}
}

object ManyToOneAndOneToManyCyclicAutoGeneratedSpec {
	case class Person(val name: String, val company: Company)
	case class Company(val name: String, employees: List[Person])

	object PersonEntity extends Entity[IntId, Person](classOf[Person]) {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("PersonSeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)
		val company = manytoone(CompanyEntity) to (_.company)

		def constructor(implicit m) = new Person(name, company) with Persisted with IntId {
			val id = m(PersonEntity.id)
		}
	}

	object CompanyEntity extends Entity[IntId, Company](classOf[Company]) {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("CompanySeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)
		val employees = onetomany(PersonEntity) to (_.employees)
		def constructor(implicit m) = new Company(name, employees) with Persisted with IntId {
			val id = m(CompanyEntity.id)
		}
	}

}