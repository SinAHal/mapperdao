package com.googlecode.mapperdao.jdbc
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * @author kostantinos.kougios
 *
 * 3 Oct 2011
 */
class QueriesSuite extends FunSuite with ShouldMatchers {
	val jdbc = Setup.setupJdbc
	val q = Queries.fromClassPath(getClass, jdbc, "/queries/test-queries.sql")

	test("1 query") {
		q.getAlias("test1") should be === List("the 1st query")
	}

	test("2 queries") {
		q.getAlias("test2") should be === List("the\n1st query", "the\n2nd query")
	}

	test("query with trailing ;") {
		val l = q.getAlias("test3")
		l should be === List("the\n1st query")
	}

	test("query with comments") {
		val l = q.getAlias("test4")
		l should be === List("the\n1st query")
	}
}