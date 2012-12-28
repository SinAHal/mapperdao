package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

/**
 * @author kostantinos.kougios
 *
 *         Jan 18, 2012
 */
@RunWith(classOf[JUnitRunner])
class ManyToManyExternalEntitySuite extends FunSuite with ShouldMatchers {
	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(ProductEntity, AttributeEntity))

	if (Setup.database == "h2") {

		test("delete without propagation") {
			createTables

			val product = Product("p1", Set(Attribute(10, "x10"), Attribute(20, "x20")))
			val inserted = mapperDao.insert(ProductEntity, product)
			mapperDao.delete(ProductEntity, inserted)
			mapperDao.select(ProductEntity, inserted.id) should be(None)
			AttributeEntity.onDeleteCalled should be === 0
		}

		test("delete with propagation") {
			createTables

			val product = Product("p1", Set(Attribute(10, "x10"), Attribute(20, "x20")))
			val inserted = mapperDao.insert(ProductEntity, product)
			mapperDao.delete(DeleteConfig(propagate = true), ProductEntity, inserted)
			mapperDao.select(ProductEntity, inserted.id) should be(None)
			AttributeEntity.onDeleteCalled should be === 1
		}

		test("persists and select") {
			createTables

			val product = Product("p1", Set(Attribute(10, "x10"), Attribute(20, "x20")))
			val inserted = mapperDao.insert(ProductEntity, product)
			inserted should be === product
			mapperDao.select(ProductEntity, inserted.id).get should be === inserted
		}

		test("updates/select, remove item") {
			createTables

			val product = Product("p1", Set(Attribute(10, "x10"), Attribute(20, "x20")))
			val inserted = mapperDao.insert(ProductEntity, product)
			val toUpdate = Product("p2", inserted.attributes.filterNot(_.id == 10))
			val updated = mapperDao.update(ProductEntity, inserted, toUpdate)
			updated should be === toUpdate
			mapperDao.select(ProductEntity, inserted.id).get should be === updated
		}
		test("updates/select, add item") {
			createTables

			val product = Product("p1", Set(Attribute(10, "x10")))
			val inserted = mapperDao.insert(ProductEntity, product)
			val toUpdate = Product("p2", inserted.attributes + Attribute(20, "x20"))
			val updated = mapperDao.update(ProductEntity, inserted, toUpdate)
			updated should be === toUpdate
			mapperDao.select(ProductEntity, inserted.id).get should be === updated
		}
	}

	def createTables {
		AttributeEntity.onDeleteCalled = 0
		Setup.dropAllTables(jdbc)
		Setup.queries(this, jdbc).update("ddl")
	}

	case class Product(val name: String, val attributes: Set[Attribute])

	case class Attribute(val id: Int, val name: String)

	object ProductEntity extends Entity[Int, SurrogateIntId, Product] {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		val attributes = manytomany(AttributeEntity) to (_.attributes)

		def constructor(implicit m) = new Product(name, attributes) with SurrogateIntId {
			val id: Int = ProductEntity.id
		}
	}

	object AttributeEntity extends ExternalEntity[Int, Attribute] {
		val id = key("id") to (_.id)

		onInsertManyToMany(ProductEntity.attributes) {
			i =>
				PrimaryKeysValues(i.foreign.id)
		}

		onSelectManyToMany(ProductEntity.attributes) {
			s =>
				s.foreignIds.map {
					case (id: Int) :: Nil =>
						Attribute(id, "x" + id)
					case _ => throw new RuntimeException
				}
		}

		onUpdateManyToMany(ProductEntity.attributes) {
			u =>
				PrimaryKeysValues(u.foreign.id)
		}

		var onDeleteCalled = 0
		onDeleteManyToMany {
			d =>
				onDeleteCalled += 1
				PrimaryKeysValues(d.foreign.id)
		}
	}

}