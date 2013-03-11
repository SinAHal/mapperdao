package com.googlecode.mapperdao

import jdbc.Setup
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

/**
 * @author kkougios
 */
@RunWith(classOf[JUnitRunner])
class UseCaseHierarchicalCategoriesSuite extends FunSuite with ShouldMatchers
{

	import UseCaseHierarchicalCategoriesSuite._

	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(CategoryEntity))

	if (Setup.database == "h2") {
		test("hierarchy") {
			createTables()
			val parent = Category("parent", None, Nil)
			val child1 = Category("child1", None, Nil)
			val child2 = Category("child2", None, Nil)
			val cat = Category("main", Some(parent), List(child1, child2))
			val i = mapperDao.insert(CategoryEntity, cat)
			i should be(cat)
		}

		def createTables() {
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update("ddl")
		}
	}

}

object UseCaseHierarchicalCategoriesSuite
{

	case class Category(name: String, parent: Option[Category], children: List[Category])

	object CategoryEntity extends Entity[Int, SurrogateIntId, Category]
	{
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		val parent = manytoone(this) foreignkey ("parent_id") option (_.parent)
		val children = onetomany(CategoryEntity) foreignkey ("parent_id") to (_.children)

		def constructor(implicit m: ValuesMap) = new Category(name, parent, children) with Stored
		{
			val id: Int = CategoryEntity.id
		}
	}

}