package com.googlecode.mapperdao.javatests.custom

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.googlecode.mapperdao.{TypeRegistry, SurrogateIntId, Entity}
import com.googlecode.mapperdao.jdbc.Setup
import com.googlecode.mapperdao.CommonEntities.createProductAttribute

/**
 * @author kkougios
 */
@RunWith(classOf[JUnitRunner])
class JavaCustomCollectionsSuite extends FunSuite with ShouldMatchers
{
	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(ProductEntity, AttributeEntity))

	test("CRUD") {
		createProductAttribute(jdbc)
		val p = new Product
		p.setName("test")
		val attrs = new java.util.HashSet[Attribute]
		val a1 = new Attribute("colour", "red")
		attrs.add(a1)
		val a2 = new Attribute("colour", "green")
		attrs.add(a2)
		p.setAttributes(new Attributes(attrs))
		val i1 = mapperDao.insert(ProductEntity, p)
		i1.getName should be("test")
		i1.getAttributes.size should be(2)
		i1.getAttributes.contains(a1) should be(true)
		i1.getAttributes.contains(a2) should be(true)

		i1.getAttributes.remove(a1)
		i1.setName("changed")
		val u1 = mapperDao.update(ProductEntity, i1)
		u1.getName should be("changed")
		u1.getAttributes.size should be(1)
		u1.getAttributes.contains(a2) should be(true)

		val s1 = mapperDao.select(ProductEntity, u1.id).get
		s1.getName should be("changed")
		s1.getAttributes.size should be(1)
		s1.getAttributes.contains(a2) should be(true)

		s1.setName("changed again")
		s1.getAttributes.add(a1)
		val u2 = mapperDao.update(ProductEntity, s1)
		u2.getName should be("changed again")
		u2.getAttributes.size should be(2)
		u2.getAttributes.contains(a1) should be(true)
		u2.getAttributes.contains(a2) should be(true)

		val s2 = mapperDao.select(ProductEntity, u2.id).get
		s2.getName should be("changed again")
		s2.getAttributes.size should be(2)
		s2.getAttributes.contains(a1) should be(true)
		s2.getAttributes.contains(a2) should be(true)
	}

	test("update from query") {
		createProductAttribute(jdbc)
		val a1 = mapperDao.insert(AttributeEntity, new Attribute("colour", "red"))
		val a2 = mapperDao.insert(AttributeEntity, new Attribute("colour", "green"))

		val i1 = mapperDao.insert(ProductEntity, new Product("test1", a1, a2))
		val i2 = mapperDao.insert(ProductEntity, new Product("test2", a1))

		import com.googlecode.mapperdao.Query._
		val products = (
			select
				from pe
				where (pe.name like "test%")
			).toList(queryDao)

		products.head.setName("changed")
		products.head.getAttributes.remove(a1)

		val List(u1, u2) = mapperDao.updateBatchMutable(ProductEntity, products)

		val List(s1, _) = (
			select
				from pe
			).toList(queryDao)

		s1.getAttributes.size should be(1)
		s1.getAttributes.contains(a2) should be(true)
		s1.getName should be("changed")
	}

	val pe = ProductEntity

	object ProductEntity extends Entity[Int, SurrogateIntId, Product]
	{
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.getName)
		val attributes = manytomany(AttributeEntity) tojava (_.getAttributes)

		def constructor(implicit m) = {
			val p = new Product with Stored
			{
				val id: Int = ProductEntity.id
			}
			p.setName(name)
			p.setAttributes(new Attributes(columnTraversableManyToManyToJList(attributes)))
			p
		}
	}

	object AttributeEntity extends Entity[Int, SurrogateIntId, Attribute]
	{
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.getName)
		val value = column("value") to (_.getValue)

		def constructor(implicit m) = {
			val a = new Attribute(name, value) with Stored
			{
				val id: Int = AttributeEntity.id
			}
			a.setName(name)
			a.setValue(value)
			a
		}
	}

}