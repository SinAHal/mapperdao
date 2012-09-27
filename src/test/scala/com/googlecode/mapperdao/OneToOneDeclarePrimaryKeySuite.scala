package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import com.googlecode.mapperdao.utils.Helpers

/**
 * @author kostantinos.kougios
 */
@RunWith(classOf[JUnitRunner])
class OneToOneDeclarePrimaryKeySuite extends FunSuite with ShouldMatchers {
	if (Setup.database == "h2") {
		implicit val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(ProductEntity, InventoryEntity))

		val p = ProductEntity
		val i = InventoryEntity

		test("crud") {
			createTables
			val i5 = mapperDao.insert(InventoryEntity, Inventory(Product(1), 5))
			val i6 = mapperDao.insert(InventoryEntity, Inventory(Product(2), 6))

			val si5 = mapperDao.select(InventoryEntity, i5.product).get
			val ui5 = mapperDao.update(InventoryEntity, si5, si5.copy(stock = 15))
			ui5 should be === Inventory(Product(1), 15)

			val rsi5 = mapperDao.select(InventoryEntity, i5.product).get
			rsi5 should be === ui5

			mapperDao.delete(InventoryEntity, rsi5)
			mapperDao.select(InventoryEntity, i5.product) should be(None)

			mapperDao.select(InventoryEntity, i6.product).get should be === i6
		}

		test("query on product") {
			createTables

			import Query._
			val inventories = for (i <- 0 to 10) yield mapperDao.insert(InventoryEntity, Inventory(Product(1 + i), 5 + i))
			val p2 = Helpers.asSurrogateIntId(inventories(2).product)
			(
				select
				from i
				join (i, i.product, p)
				where i.product === p2
			).toSet should be === Set(inventories(2))

			(
				select
				from i
				where i.stock <= 7
			).toSet should be === Set(inventories(0), inventories(1), inventories(2))

		}

		def createTables =
			{
				Setup.dropAllTables(jdbc)
				Setup.queries(this, jdbc).update("ddl")
			}
	}
	case class Inventory(val product: Product, val stock: Int)
	case class Product(val id: Int)

	object InventoryEntity extends Entity[NoId, Inventory] {
		val product = onetoone(ProductEntity) to (_.product)
		val stock = column("stock") to (_.stock)

		declarePrimaryKey(product)

		def constructor(implicit m) = new Inventory(product, stock) with NoId
	}

	object ProductEntity extends Entity[SurrogateIntId, Product] {
		val id = key("id") to (_.id)

		def constructor(implicit m) = new Product(id) with SurrogateIntId
	}
}