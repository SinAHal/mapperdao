package com.googlecode.mapperdao

import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import com.googlecode.mapperdao.jdbc.Setup

/**
 * @author kostantinos.kougios
 *
 * 6 Aug 2012
 */
@RunWith(classOf[JUnitRunner])
class TypesSuite extends FunSuite with ShouldMatchers {
	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(BDEntity))

	test("string, text, not null") {
		createTables("bd")
		val inserted = mapperDao.insert(BDEntity, BD(5, text = "x"))
		inserted should be === BD(5, text = "x")
		mapperDao.select(BDEntity, 5).get should be === inserted
	}

	test("string, text, null") {
		createTables("bd")
		val inserted = mapperDao.insert(BDEntity, BD(5, text = null))
		inserted should be === BD(5, text = null)
		mapperDao.select(BDEntity, 5).get should be === inserted
	}

	test("string, nvarchar, not null") {
		createTables("bd")
		val inserted = mapperDao.insert(BDEntity, BD(5, nvarchar = "x"))
		inserted should be === BD(5, nvarchar = "x")
		mapperDao.select(BDEntity, 5).get should be === inserted
	}

	test("string, nvarchar, null") {
		createTables("bd")
		val inserted = mapperDao.insert(BDEntity, BD(5, nvarchar = null))
		inserted should be === BD(5, nvarchar = null)
		mapperDao.select(BDEntity, 5).get should be === inserted
	}

	test("bigdecimal") {
		createTables("bd")
		val big = BigDecimal(500, 5)
		val inserted = mapperDao.insert(BDEntity, BD(5, big = big))
		inserted should be === BD(5, big)
		mapperDao.select(BDEntity, 5).get should be === inserted
	}

	test("bigdecimal, null") {
		createTables("bd")
		val inserted = mapperDao.insert(BDEntity, BD(5, big = null))
		inserted should be === BD(5)
		mapperDao.select(BDEntity, 5).get should be === inserted
	}

	test("boolean, true") {
		createTables("bd")
		val inserted = mapperDao.insert(BDEntity, BD(5, bool = true))
		inserted should be === BD(5, bool = true)
		mapperDao.select(BDEntity, 5).get should be === inserted
	}

	test("boolean, false") {
		createTables("bd")
		val inserted = mapperDao.insert(BDEntity, BD(5, bool = false))
		inserted should be === BD(5, bool = false)
		mapperDao.select(BDEntity, 5).get should be === inserted
	}

	def createTables(ddl: String) = {
		Setup.dropAllTables(jdbc)
		Setup.queries(this, jdbc).update(ddl)
	}

	case class BD(
		id: Int,
		big: BigDecimal = null,
		bool: Boolean = false,
		nvarchar: String = null,
		text: String = null)

	object BDEntity extends SimpleEntity[BD] {
		val id = key("id") to (_.id)
		val big = column("big") to (_.big)
		val bool = column("bool") to (_.bool)
		val nvarchar = column("nv") to (_.nvarchar)
		val text = column("tx") to (_.text)

		def constructor(implicit m) = new BD(id, big, bool, nvarchar, text) with Persisted
	}
}