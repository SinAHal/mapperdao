package com.googlecode.mapperdao
import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import com.googlecode.classgenerator.ReflectionManager

/**
 * @author kostantinos.kougios
 *
 * 8 Aug 2011
 */
@RunWith(classOf[JUnitRunner])
class ManyToManyLazyLoadSuite extends FunSuite with ShouldMatchers {

	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(ProductEntity, AttributeEntity))
	val reflectionManager = new ReflectionManager

	if (Setup.database == "h2") {
		test("update mutable entity") {
			createTables
			val a1 = mapperDao.insert(AttributeEntity, Attribute(6, "colour", "blue"))
			val a2 = mapperDao.insert(AttributeEntity, Attribute(9, "size", "medium"))
			val inserted = mapperDao.insert(ProductEntity, Product(2, "blue jean", Set(a1, a2)))

			val selected = mapperDao.select(SelectConfig(lazyLoad = LazyLoad(all = true)), ProductEntity, 2).get
			selected.attributes = Set(a1)
			val updated = mapperDao.update(ProductEntity, selected)
			updated should be === Product(2, "blue jean", Set(a1))
			val reloaded = mapperDao.select(ProductEntity, 2).get
			reloaded should be === updated
		}

		test("update immutable entity") {
			createTables
			val a1 = mapperDao.insert(AttributeEntity, Attribute(6, "colour", "blue"))
			val a2 = mapperDao.insert(AttributeEntity, Attribute(9, "size", "medium"))
			val inserted = mapperDao.insert(ProductEntity, Product(2, "blue jean", Set(a1, a2)))

			val selected = mapperDao.select(SelectConfig(lazyLoad = LazyLoad(all = true)), ProductEntity, 2).get
			val updated = mapperDao.update(ProductEntity, selected, Product(2, "blue jean new", Set(a1)))
			updated should be === Product(2, "blue jean new", Set(a1))
			val reloaded = mapperDao.select(ProductEntity, 2).get
			reloaded should be === updated
		}

		test("lazy load attributes") {
			createTables
			val a1 = mapperDao.insert(AttributeEntity, Attribute(6, "colour", "blue"))
			val a2 = mapperDao.insert(AttributeEntity, Attribute(9, "size", "medium"))
			val inserted = mapperDao.insert(ProductEntity, Product(2, "blue jean", Set(a1, a2)))

			val selected = mapperDao.select(SelectConfig(lazyLoad = LazyLoad(all = true)), ProductEntity, 2).get
			// use reflection to detect that the field wasn't set
			val r: Set[Attribute] = reflectionManager.get("attributes", selected)
			r should be(Set())

			verifyNotLoadded(selected)

			selected should be === Product(2, "blue jean", inserted.attributes)
			selected.attributes should be === (inserted.attributes)
		}

		test("lazy load attributes but manually updating them stops lazy loading") {
			createTables
			val a1 = mapperDao.insert(AttributeEntity, Attribute(6, "colour", "blue"))
			val a2 = mapperDao.insert(AttributeEntity, Attribute(9, "size", "medium"))
			val inserted = mapperDao.insert(ProductEntity, Product(2, "blue jean", Set(a1, a2)))

			val selected = mapperDao.select(SelectConfig(lazyLoad = LazyLoad(all = true)), ProductEntity, 2).get
			// use reflection to detect that the field wasn't set
			val r: Set[Attribute] = reflectionManager.get("attributes", selected)
			r should be(Set())

			// manually updating should stop lazy loading kick in
			selected.attributes = Set(a1)
			selected.attributes should be(Set(a1))

			verifyNotLoadded(selected)
		}
	}

	def verifyNotLoadded(o: Any) {
		val persisted = o.asInstanceOf[Persisted]
		classOf[scala.Function0[_]].isAssignableFrom(persisted.mapperDaoValuesMap.columnValue(ProductEntity.attributes.column.alias).getClass) should be(true)
	}

	def createTables =
		{
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update("ddl")
		}

	case class Product(val id: Int, val name: String, var attributes: Set[Attribute])
	case class Attribute(val id: Int, val name: String, val value: String)

	object ProductEntity extends SimpleEntity[Product] {
		val id = key("id") to (_.id)
		val name = column("name") to (_.name)
		val attributes = manytomany(AttributeEntity) getter ("attributes") to (_.attributes)
		def constructor(implicit m) = new Product(id, name, attributes) with Persisted
	}

	object AttributeEntity extends SimpleEntity[Attribute] {
		val id = key("id") to (_.id)
		val name = column("name") to (_.name)
		val value = column("value") to (_.value)

		def constructor(implicit m) = new Attribute(id, name, value) with Persisted
	}
}