package com.googlecode.mapperdao

import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import com.googlecode.mapperdao.jdbc.Setup
import com.googlecode.mapperdao.utils.Helpers._

/**
 * @author kostantinos.kougios
 *
 * 2 May 2012
 */
@RunWith(classOf[JUnitRunner])
class LinkSuite extends FunSuite with ShouldMatchers {

	if (Setup.database == "h2") {
		val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(CatEntity))

		test("linked, simple entity") {
			val c = Cat(5, "pussy cat", None)
			val linked = mapperDao.link(CatEntity, c)
			linked should be === c

			isPersisted(linked) should be(true)

			val cc = Cat(6, "child cat", Some(c))
			val linkedc = mapperDao.link(CatEntity, cc)
			linkedc should be === cc
			isPersisted(linkedc) should be(true)
		}
	}
	case class Cat(id: Int, name: String, parent: Option[Cat])

	object CatEntity extends SimpleEntity[Cat] {
		val id = key("id") to (_.id)
		val name = column("name") to (_.name)
		val parent = onetoone(this) option (_.parent)

		def constructor(implicit m) = new Cat(id, name, parent) with Persisted
	}
}