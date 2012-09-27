package com.googlecode.mapperdao

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import com.googlecode.mapperdao.jdbc.Setup

/**
 * @author kostantinos.kougios
 *
 * 19 May 2012
 */
@RunWith(classOf[JUnitRunner])
class NullValuesSuite extends FunSuite with ShouldMatchers {
	if (Setup.database == "h2") {
		val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(ValuesEntity))

		val v = Values(1, null, null, null, null)

		test("integer can be null") {
			createTables
			mapperDao.insert(ValuesEntity, v)
			mapperDao.select(ValuesEntity, 1).get.i should be(null)
		}

		test("long can be null") {
			createTables
			mapperDao.insert(ValuesEntity, v)
			mapperDao.select(ValuesEntity, 1).get.l should be(null)
		}

		test("float can be null") {
			createTables
			mapperDao.insert(ValuesEntity, v)
			mapperDao.select(ValuesEntity, 1).get.f should be(null)
		}

		test("double can be null") {
			createTables
			mapperDao.insert(ValuesEntity, v)
			mapperDao.select(ValuesEntity, 1).get.d should be(null)
		}

		def createTables =
			{
				Setup.dropAllTables(jdbc)
				Setup.queries(this, jdbc).update("ddl")
			}

	}

	case class Values(id: Int, i: java.lang.Integer, l: java.lang.Long, f: java.lang.Float, d: java.lang.Double)

	object ValuesEntity extends Entity[SurrogateIntId, Values] {
		val id = key("id") to (_.id)
		val i = column("i") to (_.i)
		val l = column("l") to (_.l)
		val f = column("f") to (_.f)
		val d = column("d") to (_.d)

		def constructor(implicit m) = new Values(id, i, l, f, d) with SurrogateIntId
	}
}