package com.googlecode.mapperdao

import com.googlecode.mapperdao.Query._
import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * @author kostantinos.kougios
 *
 * 28 Aug 2011
 */
@RunWith(classOf[JUnitRunner])
class ManyToManyQuerySuite extends FunSuite with ShouldMatchers {
	val (jdbc, mapperDao: MapperDao, queryDao: QueryDao) = Setup.setupMapperDao(TypeRegistry(ProductEntity, AttributeEntity))

	import queryDao._
	import Query._

	val p = ProductEntity
	val attr = AttributeEntity

	test("query with limits (offset only)") {
		createTables
		val a0 = mapperDao.insert(AttributeEntity, Attribute(100, "size", "46'"))
		val a1 = mapperDao.insert(AttributeEntity, Attribute(101, "size", "50'"))
		val a2 = mapperDao.insert(AttributeEntity, Attribute(102, "colour", "black"))
		val a3 = mapperDao.insert(AttributeEntity, Attribute(103, "colour", "white"))

		val p0 = mapperDao.insert(ProductEntity, Product(1, "TV 1", Set(a0, a2)))
		val p1 = mapperDao.insert(ProductEntity, Product(2, "TV 2", Set(a1, a2)))
		val p2 = mapperDao.insert(ProductEntity, Product(3, "TV 3", Set(a0, a3)))
		val p3 = mapperDao.insert(ProductEntity, Product(4, "TV 4", Set(a1, a3)))

		query(QueryConfig(offset = Some(2)), select from p).toSet should be === Set(p2, p3)
	}

	test("query with limits (limit only)") {
		createTables
		val a0 = mapperDao.insert(AttributeEntity, Attribute(100, "size", "46'"))
		val a1 = mapperDao.insert(AttributeEntity, Attribute(101, "size", "50'"))
		val a2 = mapperDao.insert(AttributeEntity, Attribute(102, "colour", "black"))
		val a3 = mapperDao.insert(AttributeEntity, Attribute(103, "colour", "white"))

		val p0 = mapperDao.insert(ProductEntity, Product(1, "TV 1", Set(a0, a2)))
		val p1 = mapperDao.insert(ProductEntity, Product(2, "TV 2", Set(a1, a2)))
		val p2 = mapperDao.insert(ProductEntity, Product(3, "TV 3", Set(a0, a3)))
		val p3 = mapperDao.insert(ProductEntity, Product(4, "TV 4", Set(a1, a3)))

		query(QueryConfig(limit = Some(2)), select from p).toSet should be === Set(p0, p1)
	}

	test("query with limits") {
		createTables
		val a0 = mapperDao.insert(AttributeEntity, Attribute(100, "size", "46'"))
		val a1 = mapperDao.insert(AttributeEntity, Attribute(101, "size", "50'"))
		val a2 = mapperDao.insert(AttributeEntity, Attribute(102, "colour", "black"))
		val a3 = mapperDao.insert(AttributeEntity, Attribute(103, "colour", "white"))

		val p0 = mapperDao.insert(ProductEntity, Product(1, "TV 1", Set(a0, a2)))
		val p1 = mapperDao.insert(ProductEntity, Product(2, "TV 2", Set(a1, a2)))
		val p2 = mapperDao.insert(ProductEntity, Product(3, "TV 3", Set(a0, a3)))
		val p3 = mapperDao.insert(ProductEntity, Product(4, "TV 4", Set(a1, a3)))

		query(QueryConfig(offset = Some(1), limit = Some(2)), select from p).toSet should be === Set(p1, p2)
	}

	test("query with skip") {
		createTables
		val a0 = mapperDao.insert(AttributeEntity, Attribute(100, "size", "46'"))
		val a1 = mapperDao.insert(AttributeEntity, Attribute(101, "size", "50'"))
		val a2 = mapperDao.insert(AttributeEntity, Attribute(102, "colour", "black"))
		val a3 = mapperDao.insert(AttributeEntity, Attribute(103, "colour", "white"))

		val p0 = mapperDao.insert(ProductEntity, Product(1, "TV 1", Set(a0, a2)))
		val p1 = mapperDao.insert(ProductEntity, Product(2, "TV 2", Set(a1, a2)))
		val p2 = mapperDao.insert(ProductEntity, Product(3, "TV 3", Set(a0, a3)))
		val p3 = mapperDao.insert(ProductEntity, Product(4, "TV 4", Set(a1, a3)))

		query(QueryConfig(skip = Set(ProductEntity.attributes)), select from p join (p, p.attributes, attr) where attr.value === "46'").toSet should be === Set(Product(1, "TV 1", Set()), Product(3, "TV 3", Set()))
	}

	test("match on FK") {
		createTables
		val a = mapperDao.insert(AttributeEntity, Attribute(100, "size", "A"))
		val b = mapperDao.insert(AttributeEntity, Attribute(101, "size", "B"))
		val c = mapperDao.insert(AttributeEntity, Attribute(102, "size", "C"))
		val d = mapperDao.insert(AttributeEntity, Attribute(103, "size", "D"))

		val p1 = mapperDao.insert(ProductEntity, Product(1, "TV 1", Set(a, b)))
		val p2 = mapperDao.insert(ProductEntity, Product(2, "TV 2", Set(c, d)))
		val p3 = mapperDao.insert(ProductEntity, Product(3, "TV 3", Set(a, c)))
		val p4 = mapperDao.insert(ProductEntity, Product(4, "TV 4", Set(d)))

		def q(attr: Attribute) = (
			select from p
			where p.attributes === attr
		)
		def qn(attr: Attribute) = (
			select from p
			where p.attributes <> attr
		)

		query(q(a)).toSet should be === Set(p1, p3)
		query(q(d)).toSet should be === Set(p2, p4)
		query(q(c)).toSet should be === Set(p2, p3)
		query(q(d)).toSet should be === Set(p2, p4)
		query(qn(d)).toSet should be === Set(p1, p2, p3)
	}

	test("order by") {
		createTables
		val a = mapperDao.insert(AttributeEntity, Attribute(100, "size", "A"))
		val b = mapperDao.insert(AttributeEntity, Attribute(101, "size", "B"))
		val c = mapperDao.insert(AttributeEntity, Attribute(102, "size", "C"))
		val d = mapperDao.insert(AttributeEntity, Attribute(103, "size", "D"))

		val p1 = mapperDao.insert(ProductEntity, Product(1, "TV 1", Set(a, b)))
		val p2 = mapperDao.insert(ProductEntity, Product(2, "TV 2", Set(c, d)))
		val p3 = mapperDao.insert(ProductEntity, Product(3, "TV 3", Set(a, c)))
		val p4 = mapperDao.insert(ProductEntity, Product(4, "TV 4", Set(d)))

		val result = query(select from p join (p, p.attributes, attr) orderBy (attr.value, asc, p.id, desc))
		result should be === List(p3, p1, p1, p3, p2, p4, p2)
	}

	test("join, 1 condition") {
		createTables
		val a0 = mapperDao.insert(AttributeEntity, Attribute(100, "size", "46'"))
		val a1 = mapperDao.insert(AttributeEntity, Attribute(101, "size", "50'"))
		val a2 = mapperDao.insert(AttributeEntity, Attribute(102, "colour", "black"))
		val a3 = mapperDao.insert(AttributeEntity, Attribute(103, "colour", "white"))

		val p0 = mapperDao.insert(ProductEntity, Product(1, "TV 1", Set(a0, a2)))
		val p1 = mapperDao.insert(ProductEntity, Product(2, "TV 2", Set(a1, a2)))
		val p2 = mapperDao.insert(ProductEntity, Product(3, "TV 3", Set(a0, a3)))
		val p3 = mapperDao.insert(ProductEntity, Product(4, "TV 4", Set(a1, a3)))

		query(select from p join (p, p.attributes, attr) where attr.value === "46'").toSet should be === Set(p0, p2)
	}

	test("join, 2 condition") {
		createTables
		val a0 = mapperDao.insert(AttributeEntity, Attribute(100, "size", "46'"))
		val a1 = mapperDao.insert(AttributeEntity, Attribute(101, "size", "50'"))
		val a2 = mapperDao.insert(AttributeEntity, Attribute(102, "colour", "black"))
		val a3 = mapperDao.insert(AttributeEntity, Attribute(103, "colour", "white"))

		val p0 = mapperDao.insert(ProductEntity, Product(1, "TV 1", Set(a0, a2)))
		val p1 = mapperDao.insert(ProductEntity, Product(2, "TV 2", Set(a1, a2)))
		val p2 = mapperDao.insert(ProductEntity, Product(3, "TV 3", Set(a0, a3)))
		val p3 = mapperDao.insert(ProductEntity, Product(4, "TV 4", Set(a1, a3)))

		query(select from p join (p, p.attributes, attr) where attr.value === "50'" or attr.value === "black").toSet should be === Set(p0, p1, p3)
	}

	def createTables =
		{
			Setup.dropAllTables(jdbc)
			jdbc.update("""
					create table Product (
						id int not null,
						name varchar(100) not null,
						primary key(id)
					)
			""")
			jdbc.update("""
					create table Attribute (
						id int not null,
						name varchar(100) not null,
						value varchar(100) not null,
						primary key(id)
					)
			""")
			jdbc.update("""
					create table Product_Attribute (
						product_id int not null,
						attribute_id int not null,
						primary key(product_id,attribute_id)
					)
			""")
		}

	case class Product(val id: Int, val name: String, val attributes: Set[Attribute])
	case class Attribute(val id: Int, val name: String, val value: String)

	object ProductEntity extends SimpleEntity(classOf[Product]) {
		val id = key("id") to (_.id)
		val name = column("name") to (_.name)
		val attributes = manytomany(AttributeEntity) to (_.attributes)

		def constructor(implicit m) = new Product(id, name, attributes) with Persisted
	}

	object AttributeEntity extends SimpleEntity(classOf[Attribute]) {
		val id = key("id") to (_.id)
		val name = column("name") to (_.name)
		val value = column("value") to (_.value)

		def constructor(implicit m) = new Attribute(id, name, value) with Persisted
	}
}