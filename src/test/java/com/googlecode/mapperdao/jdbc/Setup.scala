package com.googlecode.mapperdao.jdbc
import java.util.Properties
import org.apache.commons.dbcp.BasicDataSource
import org.apache.commons.dbcp.BasicDataSourceFactory
import org.scala_tools.time.Imports._
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.LoggerFactory
import com.googlecode.mapperdao.drivers.Derby
import com.googlecode.mapperdao.drivers.Mysql
import com.googlecode.mapperdao.drivers.Oracle
import com.googlecode.mapperdao.drivers.PostgreSql
import com.googlecode.mapperdao.DefaultTypeManager
import com.googlecode.mapperdao.MapperDao
import com.googlecode.mapperdao.QueryDao
import com.googlecode.mapperdao.TypeRegistry
import com.googlecode.mapperdao.events.Events
import com.googlecode.mapperdao.drivers.Driver
import com.googlecode.mapperdao.drivers.SqlServer

/**
 * creates an environment for specs
 *
 * @author kostantinos.kougios
 *
 * 31 Jul 2011
 */
object Setup {
	private val logger: Logger = LoggerFactory.getLogger(getClass)

	val typeManager = new DefaultTypeManager
	def database = {
		val d = System.getProperty("database")
		if (d == null) throw new IllegalStateException("please define database via -Ddatabase=postgresql")
		d
	}

	def now = DateTime.now.withMillisOfSecond(0)

	private var jdbc: Jdbc = null
	def setupJdbc: Jdbc = if (jdbc == null) {
		val properties = new Properties
		logger.debug("connecting to %s".format(database))
		properties.load(getClass.getResourceAsStream("/jdbc.test.%s.properties".format(database)))
		val dataSource = BasicDataSourceFactory.createDataSource(properties).asInstanceOf[BasicDataSource]
		jdbc = Jdbc(dataSource, typeManager)
		jdbc
	} else jdbc

	def setupMapperDao(typeRegistry: TypeRegistry, events: Events = new Events): (Jdbc, Driver, MapperDao) =
		{
			val jdbc = setupJdbc
			val driver = database match {
				case "postgresql" => new PostgreSql(jdbc, typeRegistry)
				case "mysql" => new Mysql(jdbc, typeRegistry)
				case "oracle" => new Oracle(jdbc, typeRegistry)
				case "derby" => new Derby(jdbc, typeRegistry)
				case "sqlserver" => new SqlServer(jdbc, typeRegistry)
			}
			val mapperDao = MapperDao(driver, events)
			(jdbc, driver, mapperDao)
		}

	def setupQueryDao(typeRegistry: TypeRegistry): (Jdbc, MapperDao, QueryDao) =
		{
			val (jdbc, driver, mapperDao) = setupMapperDao(typeRegistry)
			(jdbc, mapperDao, QueryDao(typeRegistry, driver, mapperDao))
		}

	def dropAllTables(jdbc: Jdbc): Int =
		{
			var errors = 0
			database match {
				case "postgresql" =>
					jdbc.queryForList("select table_name from information_schema.tables where table_schema='public'").foreach { m =>
						val table = m("table_name")
						try {
							jdbc.update("""drop table "%s" cascade""".format(table))
						} catch {
							case e: Throwable =>
								println(e.getMessage)
								errors += 1
						}
					}
				case "mysql" =>
					jdbc.queryForList("show tables").foreach { m =>
						val table = m("Tables_in_testcases")
						try {
							jdbc.update("drop table %s".format(table))
						} catch {
							case e: Throwable =>
								println(e.getMessage)
								errors += 1
						}
					}
				case "oracle" =>
					jdbc.queryForList("select table_name from user_tables").foreach { m =>
						val table = m("TABLE_NAME")
						try {
							jdbc.update("""drop table "%s"""".format(table))
						} catch {
							case e: Throwable =>
								println(e.getMessage)
								errors += 1
						}
					}
				case "derby" =>
					jdbc.queryForList("select tablename from sys.SYSTABLES where tabletype='T'").foreach { m =>
						val table = m("tablename") match {
							case "User" => """"User""""
							case t => t
						}
						try {
							jdbc.update("drop table %s".format(table))
						} catch {
							case e: Throwable =>
								println(e.getMessage)
								errors += 1
						}
					}
				case "sqlserver" =>
					jdbc.queryForList("select name from sysobjects where xtype='U'").foreach { m =>
						val table = m("name")
						try {
							jdbc.update("drop table %s".format(table))
						} catch {
							case e: Throwable =>
								println(e.getMessage)
								errors += 1
						}
					}
			}
			if (errors > 0) dropAllTables(jdbc) else 0
		}

	def createMySeq(jdbc: Jdbc) = createSeq(jdbc, "myseq")

	def createSeq(jdbc: Jdbc, name: String) {
		try {
			database match {
				case "derby" => jdbc.update("drop sequence %s RESTRICT".format(name))
				case _ => jdbc.update("drop sequence %s".format(name))
			}
		} catch {
			case e: Exception => println(e)
		}
		jdbc.update("create sequence %s".format(name))
	}

	def oracleTrigger(jdbc: Jdbc, table: String) {
		jdbc.update("""
					create or replace trigger ti_autonumber
					before insert on %s for each row
					begin
						select myseq.nextval into :new.id from dual;
					end;
				""".format(table))

	}

	def queries(o: AnyRef, jdbc: Jdbc) = Queries.fromClassPath(o.getClass, jdbc, "/sql/%s.%s.sql".format(o.getClass.getSimpleName, database))
}