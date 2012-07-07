package com.googlecode.mapperdao
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import com.googlecode.mapperdao.jdbc.Setup

/**
 * an end-to-end test suite for many-to-many over a trait with multiple implementations
 *
 * @author kostantinos.kougios
 *
 * 7 Jul 2012
 */
@RunWith(classOf[JUnitRunner])
class UseCaseManyToManyForTraitSuite extends FunSuite with ShouldMatchers {

	if (Setup.database == "h2") {
		val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(PersonEntity, CompanyEntity, ContactListEntity))

		test("CRUD") {
			createTables()

			val nick = mapperDao.insert(PersonEntity, Person("nick", 30))
			val filipos = Person("filipos", 30)
			val cl1 = ContactList("list1", Set(Person("kostas", 20), nick, Company("com1", "xx55")))
			val inserted1 = mapperDao.insert(ContactListEntity, cl1)
			inserted1 should be === cl1

			val cl2 = ContactList("list2", Set(Person("alexandros", 20), nick, filipos, Company("mc5", "gj4xxx")))
			val inserted2 = mapperDao.insert(ContactListEntity, cl2)
			inserted2 should be === cl2

			mapperDao.select(ContactListEntity, inserted1.id).get should be === cl1
			mapperDao.select(ContactListEntity, inserted2.id).get should be === cl2

			val updated2 = inserted2.copy(parties = inserted2.parties + nick)
			mapperDao.update(ContactListEntity, inserted2, updated2) should be === updated2

			mapperDao.select(ContactListEntity, inserted2.id).get should be === updated2
			mapperDao.select(ContactListEntity, inserted1.id).get should be === cl1

			mapperDao.delete(ContactListEntity, inserted1)
			mapperDao.select(ContactListEntity, inserted2.id).get should be === updated2
			mapperDao.select(ContactListEntity, inserted1.id) should be === None

			mapperDao.delete(PersonEntity, nick)
			mapperDao.select(ContactListEntity, inserted2.id).get should be === ContactList("list2", Set(Person("alexandros", 20), filipos, Company("mc5", "gj4xxx")))
		}

		def createTables() {
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update("ddl")
		}
	}

	trait Party {
		val name: String
	}

	case class Person(name: String, age: Double) extends Party

	case class Company(name: String, registration: String) extends Party

	case class ContactList(name: String, parties: Set[Party]) // many-to-many against Person *and* Company

	object PersonEntity extends Entity[IntId, Person] {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		val age = column("age") to (_.age)

		def constructor(implicit m: ValuesMap) = new Person(name, age) with IntId with Persisted {
			val id: Int = PersonEntity.id
		}
	}

	object CompanyEntity extends Entity[IntId, Company] {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		val registration = column("registration") to (_.registration)

		def constructor(implicit m: ValuesMap) = new Company(name, registration) with IntId with Persisted {
			val id: Int = CompanyEntity.id
		}
	}

	object ContactListEntity extends Entity[IntId, ContactList] {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		val people = manytomany(PersonEntity) to (_.parties.collect {
			case p: Person => p
		})
		val companies = manytomany(CompanyEntity) to (_.parties.collect {
			case c: Company => c
		})

		def constructor(implicit m: ValuesMap) = {
			val cs = m(companies).toSet
			val ppl = m(people).toSet
			new ContactList(name, ppl ++ cs) with IntId with Persisted {
				val id: Int = ContactListEntity.id
			}
		}
	}
}