package com.googlecode.mapperdao

import org.specs2.mutable.SpecificationWithJUnit
import com.googlecode.mapperdao.jdbc.Setup

/**
 * @author kostantinos.kougios
 *
 * 30 Aug 2011
 */
class OneToOneMutableTwoWaySpec extends SpecificationWithJUnit {
	import OneToOneMutableTwoWaySpec._
	val (jdbc, mapperDao) = Setup.setupMapperDao(TypeRegistry(ProductEntity, InventoryEntity))

	import mapperDao._

	"update id of primary entity" in {
		createTables
		val product = Product(1, Inventory(null, 5))
		product.inventory.product = product
		val inserted = insert(ProductEntity, product)
		inserted.id = 2
		val updated = update(ProductEntity, inserted)
		updated must_== inserted
		val recreatedProduct = Product(2, Inventory(null, 5))
		recreatedProduct.inventory.product = recreatedProduct
		select(ProductEntity, 2).get must_== recreatedProduct
		select(ProductEntity, 1) must beNone
	}

	"CRUD mutable" in {
		createTables
		val product = Product(1, Inventory(null, 5))
		product.inventory.product = product
		val inserted = insert(ProductEntity, product)
		inserted.inventory.stock = 8
		val updated = update(ProductEntity, inserted)
		updated must_== inserted
		val selected = select(ProductEntity, updated.id).get
		selected must_== updated

		delete(ProductEntity, selected)
		select(ProductEntity, selected.id) must beNone
	}

	"insert & select mutable" in {
		createTables
		val product = Product(1, Inventory(null, 5))
		product.inventory.product = product
		val inserted = insert(ProductEntity, product)
		inserted must_== product
		val selected = select(ProductEntity, inserted.id).get
		selected must_== inserted
	}

	"from null to value" in {
		createTables
		val product = Product(1, null)
		val inserted = insert(ProductEntity, product)
		inserted.inventory = Inventory(inserted, 5)
		val updated = update(ProductEntity, inserted)
		updated.inventory must_== inserted.inventory
		select(ProductEntity, inserted.id).get must_== updated

		delete(ProductEntity, updated)
		select(ProductEntity, updated.id) must beNone
	}

	"update to null" in {
		createTables
		val product = Product(1, Inventory(null, 5))
		product.inventory.product = product
		val inserted = insert(ProductEntity, product)
		inserted.inventory = null
		val updated = update(ProductEntity, inserted)
		updated.inventory must_== null
		select(ProductEntity, inserted.id).get must_== updated
	}

	def createTables =
		{
			jdbc.update("drop table if exists Product cascade")
			jdbc.update("drop table if exists Inventory cascade")

			jdbc.update("""
				create table Product (
					id int not null,
					primary key (id)
				)
			""")
			jdbc.update("""
				create table Inventory (
					product_id int not null,
					stock int not null,
					primary key (product_id),
					foreign key (product_id) references Product(id) on delete cascade on update cascade
				)
			""")
		}
}

object OneToOneMutableTwoWaySpec {
	case class Inventory(var product: Product, var stock: Int) {
		override def hashCode = stock
		override def equals(v: Any) = v match {
			case i: Inventory => i.stock == stock && ((i.product == null && product == null) || (i.product != null && product != null && i.product.id == product.id))
			case _ => false
		}
		override def toString = "Inventory(%d, productId:%d)".format(stock, if (product == null) null else product.id)
	}
	case class Product(var id: Int, var inventory: Inventory)

	object InventoryEntity extends SimpleEntity[Inventory](classOf[Inventory]) {
		val product = oneToOne(classOf[Product], _.product)
		val stock = int("stock", _.stock)

		val constructor = (m: ValuesMap) => new Inventory(m(product), m(stock)) with Persisted {
			val valuesMap = m
		}
	}

	object ProductEntity extends SimpleEntity[Product](classOf[Product]) {
		val id = pk("id", _.id)
		val inventory = oneToOneReverse(classOf[Inventory], _.inventory)

		val constructor = (m: ValuesMap) => new Product(m(id), m(inventory)) with Persisted {
			val valuesMap = m
		}
	}
}