package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.Matchers._
import org.scalatest.junit.JUnitRunner
/**
 * @author kostantinos.kougios
 *
 *         20 Aug 2011
 */
@RunWith(classOf[JUnitRunner])
class ManyToOneQuerySuite extends FunSuite
{

	import com.googlecode.mapperdao.ManyToOneQuerySuite._

	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(List(PersonEntity, HouseEntity, AddressEntity))

	import mapperDao._
	import queryDao._

	test("query with aliases") {
		createTables()
		val (p0, p1, p2, _, _) = testData1
		import com.googlecode.mapperdao.Query._
		query(
			select from (pe as 'x)
				join(pe as 'x, pe.lives, he as 'y)
				join(he as 'y, he.address, ad as 'z)
				where ('z, ad.postCode) === "SE1 1AA"
		) should be(List(p0, p1, p2))
	}

	test("join on") {
		createTables()
		val (p0, p1, p2, _, _) = testData1
		import com.googlecode.mapperdao.Query._
		query(
			select from (pe as 'x)
				join(pe as 'x, pe.lives, he as 'y)
				join(he as 'y, he.address, ad as 'z) on ('z, ad.postCode) === "SE1 1AA"
		) should be(List(p0, p1, p2))
	}

	test("query 2 level join") {
		createTables()
		val (p0, p1, p2, _, _) = testData1
		import com.googlecode.mapperdao.Query._
		query(
			select from pe
				join(pe, pe.lives, he)
				join(he, he.address, ad)
				where ad.postCode === "SE1 1AA"
		) should be(List(p0, p1, p2))
	}

	test("query with limits (offset only)") {
		createTables()
		val (_, _, p2, p3, p4) = testData1

		query(QueryConfig(offset = Some(2)), q0Limits).toSet should be(Set(p2, p3, p4))
	}

	test("query with limits (limit only)") {
		createTables()
		val (p0, p1, _, _, _) = testData1

		query(QueryConfig(limit = Some(2)), q0Limits).toSet should be(Set(p0, p1))
	}

	test("query with limits") {
		createTables()
		val (_, _, p2, _, _) = testData1

		query(QueryConfig(offset = Some(2), limit = Some(1)), q0Limits).toSet should be(Set(p2))
	}

	test("query with skip") {
		createTables()
		testData1

		import com.googlecode.mapperdao.Query._
		query(
			QueryConfig(skip = Set(PersonEntity.lives)),
			select from pe join(pe, pe.lives, he) where he.name === "Block B"
		) should be(List(Person(3, "p3", null), Person(4, "p4", null)))
	}

	test("query on FK for null") {
		createTables()
		val (p0, p1, p2, p3, p4) = testData1
		val p5 = insert(PersonEntity, Person(5, "p5", null))
		val p6 = insert(PersonEntity, Person(6, "p6", null))
		query(q3(null)) should be(List(p5, p6))
		query(q3n(null)) should be(List(p0, p1, p2, p3, p4))
	}

	test("query on FK") {
		createTables()
		val (p0, p1, p2, p3, p4) = testData1
		query(q3(p4.lives)) should be(List(p3, p4))
		query(q3(p0.lives)) should be(List(p0, p1, p2))
		query(q3n(p0.lives)) should be(List(p3, p4))
	}

	test("query 1 level join") {
		createTables()
		val (_, _, _, p3, p4) = testData1

		import com.googlecode.mapperdao.Query._
		query(select from pe join(pe, pe.lives, he) where he.name === "Block B") should be(List(p3, p4))
	}

	test("query 2 level join with or") {
		createTables()
		val (p0, p1, p2, p3, p4) = testData1
		import com.googlecode.mapperdao.Query._
		query(select from pe join
			(pe, pe.lives, he) join
			(he, he.address, ad) where
			ad.postCode === "SE1 1AA" or
			ad.postCode === "SE2 2BB") should be(List(p0, p1, p2, p3, p4))
	}

	def testData1 = {
		createTables()
		val List(a0, a1) = insertBatch(AddressEntity, List(Address(100, "SE1 1AA"), Address(101, "SE2 2BB")))
		val List(h0, h1) = insertBatch(HouseEntity, List(House(10, "Appartment A", a0), House(11, "Block B", a1)))

		val List(p0, p1, p2, p3, p4) = insertBatch(PersonEntity, List(
			Person(0, "p0", h0),
			Person(1, "p1", h0),
			Person(2, "p2", h0),
			Person(3, "p3", h1),
			Person(4, "p4", h1)
		))
		(p0, p1, p2, p3, p4)
	}

	def createTables() {
		Setup.dropAllTables(jdbc)
		jdbc.update( """
			create table Address (
				id int not null,
				postcode varchar(8) not null,
				primary key (id)
			)
					 """)
		jdbc.update( """
			create table House (
				id int not null,
				name varchar(30) not null,
				address_id int not null,
				primary key (id),
				foreign key (address_id) references Address(id) on delete cascade
			)
					 """)
		jdbc.update( """
			create table Person (
				id int not null,
				name varchar(30) not null,
				lives_id int,
				primary key (id),
				foreign key (lives_id) references House(id) on delete cascade
			)
					 """)
	}
}

object ManyToOneQuerySuite
{

	import com.googlecode.mapperdao.Query._

	val pe = PersonEntity
	val he = HouseEntity
	val ad = AddressEntity

	val q0Limits = select from pe

	def q3(h: House) = (
		select from pe
			where pe.lives === h
		)

	def q3n(h: House) = (
		select from pe
			where pe.lives <> h
		)

	case class Person(id: Int, var name: String, lives: House)

	case class House(id: Int, name: String, address: Address)

	case class Address(id: Int, postCode: String)

	object PersonEntity extends Entity[Int, SurrogateIntId, Person]
	{
		val id = key("id") to (_.id)
		val name = column("name") to (_.name)
		val lives = manytoone(HouseEntity) foreignkey "lives_id" to (_.lives)

		def constructor(implicit m: ValuesMap) = new Person(id, name, lives) with Stored
	}

	object HouseEntity extends Entity[Int, SurrogateIntId, House]
	{
		val id = key("id") to (_.id)
		val name = column("name") to (_.name)
		val address = manytoone(AddressEntity) to (_.address)

		def constructor(implicit m: ValuesMap) = new House(id, name, address) with Stored
	}

	object AddressEntity extends Entity[Int, SurrogateIntId, Address]
	{
		val id = key("id") to (_.id)
		val postCode = column("postcode") to (_.postCode)

		def constructor(implicit m: ValuesMap) = new Address(id, postCode) with Stored
	}

}