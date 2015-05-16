package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.Matchers._
import org.scalatest.junit.JUnitRunner
/**
 * @author kostantinos.kougios
 *
 *         21 Sep 2012
 */
@RunWith(classOf[JUnitRunner])
class MergeSuite extends FunSuite
{
	if (Setup.database == "h2") {
		import com.googlecode.mapperdao.CommonEntities._
		val (jdbc, mapperDao, _) = Setup.setupMapperDao(List(PersonEntity, CompanyEntity))
		val company = Company("acme")

		test("merge entity") {
			createPersonCompany(jdbc)
			val person = Person("person 1", company)
			val inserted = mapperDao.insert(PersonEntity, person)
			val upd = Person("person 1 updated", inserted.company)
			val merged = mapperDao.merge(PersonEntity, upd, inserted.id)
			merged should be(upd)
			mapperDao.select(PersonEntity, inserted.id).get should be(merged)
		}

		test("merge and replace") {
			createPersonCompany(jdbc)
			val person = Person("person 1", company)
			val inserted = mapperDao.insert(PersonEntity, person)
			val upd = Person("person 1 updated", replace(inserted.company, inserted.company.copy(name = "updated")))
			val merged = mapperDao.merge(PersonEntity, upd, inserted.id)
			merged should be(upd)
			mapperDao.select(PersonEntity, inserted.id).get should be(merged)

			jdbc.queryForInt("select count(*) from company") should be(1)
		}
	}
}
