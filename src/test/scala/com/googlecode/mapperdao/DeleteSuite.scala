package com.googlecode.mapperdao

import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import com.googlecode.mapperdao.jdbc.Setup

/**
 * @author kostantinos.kougios
 *
 * 17 Oct 2012
 */
@RunWith(classOf[JUnitRunner])
class DeleteSuite extends FunSuite with ShouldMatchers {
	import CommonEntities._

	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(ProductEntity, AttributeEntity))

	test("delete all") {
		createProductAttribute(jdbc)
		createTestData

		import Delete._
		val pe = ProductEntity
		(delete from pe).run(queryDao).rowsAffected should be(2)

		import Query._
		(select from pe).toList(queryDao) should be(Nil)
	}

	test("delete simple where clause") {
		createProductAttribute(jdbc)
		val (p1, p2) = createTestData

		import Delete._
		val pe = ProductEntity
		//		(delete from pe where pe.name === "cpu").run(queryDao).rowsAffected should be(1)
		//
		//		import Query._
		//		(select from pe).toList(queryDao) should be(List(p2))
	}

	def createTestData = {
		val a1 = mapperDao.insert(AttributeEntity, Attribute("colour", "red"))
		val a2 = mapperDao.insert(AttributeEntity, Attribute("size", "10"))

		val p1 = mapperDao.insert(ProductEntity, Product("cpu", Set(a1)))
		val p2 = mapperDao.insert(ProductEntity, Product("ram", Set(a2)))
		(p1, p2)
	}
}
