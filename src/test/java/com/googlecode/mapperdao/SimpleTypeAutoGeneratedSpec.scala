package com.googlecode.mapperdao

import org.specs2.mutable.SpecificationWithJUnit

import com.googlecode.mapperdao.jdbc.Setup

/**
 * @author kostantinos.kougios
 *
 * 2 Sep 2011
 */
class SimpleTypeAutoGeneratedSpec extends SpecificationWithJUnit {
	import SimpleTypeAutoGeneratedSpec._

	val typeRegistry = TypeRegistry(JobPositionEntity)
	val (jdbc, mapperDao) = Setup.setupMapperDao(typeRegistry)

	"CRUD using sequences" in {
		Setup.database match {
			case "postgresql" | "oracle" =>
				createJobPositionTable(true)
				val jp = new JobPosition("Developer")
				val inserted = mapperDao.insert(JobPositionEntity, jp)
				inserted must_== jp
				inserted.id must_== 1

				// now load
				val loaded = mapperDao.select(JobPositionEntity, inserted.id).get
				loaded must_== jp

				// update
				loaded.name = "Scala Developer"
				val afterUpdate = mapperDao.update(JobPositionEntity, loaded).asInstanceOf[Persisted]
				afterUpdate.valuesMap(JobPositionEntity.name) must_== "Scala Developer"
				afterUpdate must_== loaded

				val reloaded = mapperDao.select(JobPositionEntity, inserted.id).get
				reloaded must_== loaded

				mapperDao.delete(JobPositionEntity, reloaded)

				mapperDao.select(JobPositionEntity, inserted.id) must_== None
				success
			case "mysql" | "derby" => success
		}
	}

	"CRUD auto-increment" in {
		Setup.database match {
			case "oracle" => success
			case _ =>
				createJobPositionTable(false)

				val jp = new JobPosition("Developer")
				val inserted = mapperDao.insert(JobPositionEntity, jp)
				inserted must_== jp

				// now load
				val loaded = mapperDao.select(JobPositionEntity, inserted.id).get
				loaded must_== jp

				// update
				loaded.name = "Scala Developer"
				val afterUpdate = mapperDao.update(JobPositionEntity, loaded).asInstanceOf[Persisted]
				afterUpdate.valuesMap(JobPositionEntity.name) must_== "Scala Developer"
				afterUpdate must_== loaded

				val reloaded = mapperDao.select(JobPositionEntity, inserted.id).get
				reloaded must_== loaded

				mapperDao.delete(JobPositionEntity, reloaded)

				mapperDao.select(JobPositionEntity, inserted.id) must_== None
				success
		}
	}

	def createJobPositionTable(sequences: Boolean) {
		Setup.dropAllTables(jdbc)
		if (sequences) Setup.createMySeq(jdbc)
		Setup.database match {
			case "postgresql" =>
				val idDecl = if (sequences) "id int not null default nextval('myseq')" else "id serial not null"
				jdbc.update("""
					create table JobPosition (
					%s,
					name varchar(100) not null,
					primary key (id)
				)""".format(idDecl))
			case "oracle" =>
				jdbc.update("""
					create table JobPosition (
					id int not null,
					name varchar(100) not null,
					primary key (id)
				)""")
				Setup.oracleTrigger(jdbc, "JobPosition")

			case "mysql" =>
				jdbc.update("""
					create table JobPosition (
					id int not null AUTO_INCREMENT,
					name varchar(100) not null,
					primary key (id)
				) engine InnoDB""")
			case "derby" =>
				jdbc.update("""
					create table JobPosition (
					id int not null GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
					name varchar(100) not null,
					primary key (id)
				)""")
		}
	}
}

object SimpleTypeAutoGeneratedSpec {
	case class JobPosition(var name: String)

	object JobPositionEntity extends Entity[IntId, JobPosition](classOf[JobPosition]) {
		val id = intAutoGeneratedPK("id", _.id)
		val name = string("name", _.name)

		def constructor(implicit m: ValuesMap) = new JobPosition(name) with IntId with Persisted {
			// we force the value to int cause mysql AUTO_GENERATED always returns Long instead of Int
			val id: Int = JobPositionEntity.id
		}
	}
}