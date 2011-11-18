package com.googlecode.mapperdao.jdbc

import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.specification.BeforeExample
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

/**
 * @author kostantinos.kougios
 *
 * 30 Aug 2011
 */
@RunWith(classOf[JUnitRunner])
class TransactionSpec extends SpecificationWithJUnit with BeforeExample {

	private val jdbc = Setup.setupJdbc

	import Transaction._
	val txManager = Transaction.transactionManager(jdbc)
	val tx = Transaction.get(txManager, Propagation.Nested, Isolation.ReadCommited, -1)

	def before = {
		Setup.dropAllTables(jdbc)
		Setup.queries(this, jdbc).update("ddl")
	}

	"commit" in {
		tx { () =>
			for (i <- 1 to 5) jdbc.update("insert into tx(id,name) values(?,?)", i, "x" + i);
		}

		jdbc.queryForInt("select count(*) from tx") must_== 5
	}

	"rollback" in {
		try {
			tx { () =>
				for (i <- 1 to 5) jdbc.update("insert into tx(id,name) values(?,?)", i, "x" + i);
				throw new IllegalStateException
			}
		} catch {
			case e: IllegalStateException => // ignore
		}

		jdbc.queryForInt("select count(*) from tx") must_== 0
	}

	"manual rollback" in {
		tx { status =>
			for (i <- 1 to 5) jdbc.update("insert into tx(id,name) values(?,?)", i, "x" + i);
			status.setRollbackOnly
		}

		jdbc.queryForInt("select count(*) from tx") must_== 0
	}
}