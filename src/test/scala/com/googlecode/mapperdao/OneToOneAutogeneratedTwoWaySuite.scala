package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * @author kostantinos.kougios
 *
 * 5 Sep 2011
 */
@RunWith(classOf[JUnitRunner])
class OneToOneAutogeneratedTwoWaySuite extends FunSuite with ShouldMatchers {
	import OneToOneAutogeneratedTwoWaySpec._
	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(ProductEntity, InventoryEntity))

	test("delete, DeleteConfig(true)") {
		createTables(false)
		val product = Product(Inventory(null, 5), 0)
		product.inventory.product = product
		val inserted = mapperDao.insert(ProductEntity, product)

		mapperDao.delete(DeleteConfig(true), ProductEntity, inserted)
		jdbc.queryForInt("select count(*) from Product") should be === 0
		jdbc.queryForInt("select count(*) from Inventory") should be === 0
	}

	test("delete, DeleteConfig(true,skip)") {
		createTables(false)
		val product = Product(Inventory(null, 5), 0)
		product.inventory.product = product
		val inserted = mapperDao.insert(ProductEntity, product)

		mapperDao.delete(DeleteConfig(true, skip = Set(ProductEntity.inventory)), ProductEntity, inserted)
		jdbc.queryForInt("select count(*) from Product") should be === 0
		jdbc.queryForInt("select count(*) from Inventory") should be === 1
	}

	test("select, skip one-to-one") {
		createTables(true)
		val product = Product(Inventory(null, 5), 0)
		product.inventory.product = product
		val inserted = mapperDao.insert(ProductEntity, product)

		val selected = mapperDao.select(SelectConfig(skip = Set(InventoryEntity.product)), ProductEntity, List(inserted.id)).get
		selected.inventory.product should be(null)
		selected.inventory should be === Inventory(null, 5)
	}

	test("select, skip one-to-one-reverse") {
		createTables(true)
		val product = Product(Inventory(null, 5), 0)
		product.inventory.product = product
		val inserted = mapperDao.insert(ProductEntity, product)

		val selected = mapperDao.select(SelectConfig(skip = Set(ProductEntity.inventory)), ProductEntity, List(inserted.id)).get
		selected.inventory should be(null)
	}

	test("update to null") {
		createTables(true)
		val product = Product(Inventory(null, 5), 0)
		product.inventory.product = product
		val inserted = mapperDao.insert(ProductEntity, product)
		inserted.inventory = null
		val updated = mapperDao.update(ProductEntity, inserted)
		updated.inventory should be === null
		mapperDao.select(ProductEntity, inserted.id).get should be === updated
	}

	test("CRUD mutable") {
		createTables(true)
		val product = Product(Inventory(null, 5), 0)
		product.inventory.product = product
		val inserted = mapperDao.insert(ProductEntity, product)
		inserted.inventory.stock = 8
		val updated = mapperDao.update(ProductEntity, inserted)
		updated should be === inserted
		val selected = mapperDao.select(ProductEntity, updated.id).get
		selected should be === updated

		mapperDao.delete(ProductEntity, selected)
		mapperDao.select(ProductEntity, selected.id) should be(None)
	}

	test("insert & select mutable") {
		createTables(true)
		val product = Product(Inventory(null, 5), 0)
		product.inventory.product = product
		val inserted = mapperDao.insert(ProductEntity, product)
		inserted.inventory.stock should be === product.inventory.stock
		val selected = mapperDao.select(ProductEntity, inserted.id).get
		selected should be === inserted
	}

	test("from null to value") {
		createTables(true)
		val product = Product(null, 0)
		val inserted = mapperDao.insert(ProductEntity, product)
		inserted.inventory = Inventory(inserted, 5)
		val updated = mapperDao.update(ProductEntity, inserted)
		updated.inventory should be === inserted.inventory
		mapperDao.select(ProductEntity, inserted.id).get should be === updated

		mapperDao.delete(ProductEntity, updated)
		mapperDao.select(ProductEntity, updated.id) should be(None)
	}

	def createTables(cascade: Boolean) =
		{
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update(if (cascade) "cascade" else "nocascade")
			Setup.database match {
				case "postgresql" | "h2" =>
					Setup.createMySeq(jdbc)
				case "oracle" =>
					Setup.createMySeq(jdbc)
					Setup.oracleTrigger(jdbc, "Product")
				case _ =>
			}
		}

}

object OneToOneAutogeneratedTwoWaySpec {
	case class Inventory(var product: Product, var stock: Int) {
		override def hashCode = stock
		override def equals(v: Any) = v match {
			case i: Inventory => i.stock == stock && ((i.product == null && product == null) || (i.product != null && product != null && i.product == product))
			case _ => false
		}
		override def toString = "Inventory(%d, productId:%s)".format(stock, product match {
			case p: Product with SurrogateIntId => p.id
			case p: Product => "Product(not persisted)"
			case null => null
		})
	}
	case class Product(var inventory: Inventory, val x: Int) {
		override def equals(v: Any) = v match {
			case p: Product with SurrogateIntId =>
				this match {
					case tp: Product with SurrogateIntId => tp.id == p.id
					case _ => false
				}
			case _ => false
		}
	}

	object InventoryEntity extends Entity[NoId, Inventory] {
		val product = onetoone(ProductEntity) to (_.product)
		val stock = column("stock") to (_.stock)

		def constructor(implicit m) = new Inventory(product, stock) with NoId
	}

	val sequence = Setup.database match {
		case "postgresql" | "oracle" | "h2" => Some("myseq")
		case _ => None
	}
	object ProductEntity extends Entity[SurrogateIntId, Product] {
		val id = key("id") sequence (sequence) autogenerated (_.id)
		val inventory = onetoonereverse(InventoryEntity) to (_.inventory)
		val x = column("x") to (_.x)

		def constructor(implicit m) = new Product(inventory, x) with SurrogateIntId {
			val id: Int = ProductEntity.id
		}
	}

}