## Summary ##
MapperDao is an ORM library for the scala language and the following databases:
  * oracle
  * postgresql
  * mysql
  * derby
  * sql server
  * h2

It allows
[...(more)](wiki/Summary.md)

## News ##
  * 05/04/2015 : moved to github, please note links might be broken.
  * 25/08/2014 : updated the [tutorial](documentation/tutorial.pdf)
  * 08/06/2014 : 1.0.1 for scala 2.10 & 2.11 is available, a maintenance release. The artifactId now complies with sbt rules reg. scala version. Also minor clean up of the exposed API and code.
  * 21/04/2014 : 1.0.0.2.11 is now released for scala 2.11 .
  * 20/04/2014 : 1.0.0.2.10 is now released for scala 2.10 .
  * 18/01/2014 : 1.0.0.2.10.3-SNAPSHOT with immutable query DSL, better [aliasing](wiki/QueryAlias.md) and [immutable builder](wiki/DynamicQueries.md)

[...(more)](wiki/News.md)

## Quick Links ##

  * [mapperdao tutorial (pdf)](documentation/tutorial.pdf)

  * [setup, configuration and usage documentation](wiki/TableOfContents.md)

  * [Examples](https://code.google.com/p/mapperdao-examples/)

  * [Sbt/Maven Configuration](wiki/MavenConfiguration.md)

  * [Discussions group](http://groups.google.com/group/mapperdao)

  * [F.A.Q.](wiki/FAQ.md)

## Example ##

```
import java.util.Properties
import org.apache.commons.dbcp.BasicDataSourceFactory

// create a datasource using apache dbcp
val properties = new Properties
properties.load(getClass.getResourceAsStream("/jdbc.test.properties"))
val dataSource = BasicDataSourceFactory.createDataSource(properties)

// create the mapperdao instance, connect to an oracle database and register our 2 entities
import com.googlecode.mapperdao.utils.Setup
val (jdbc,mapperDao,queryDao,txManager) = Setup.oracle(dataSource,List(PersonEntity,CompanyEntity))

// domain model classes (immutable)
class Person(val name: String, val company: Company)
class Company(val name: String)

// mappings (using default table and column naming convention)
object PersonEntity extends Entity[Int,SurrogateIntId, Person] {
	val id = key("id") autogenerated (_.id)
	val name = column("name") to (_.name)
	val company = manytoone(CompanyEntity) to (_.company)

	def constructor(implicit m) = new Person(name, company) with Stored {
		val id: Int = PersonEntity.id
	}
}

object CompanyEntity extends Entity[Int,SurrogateIntId, Company] {
	val id = key("id") autogenerated (_.id)
	val name = column("name") to (_.name)

	def constructor(implicit m) = new Company(name) with Stored {
		val id: Int = CompanyEntity.id
	}
}

val tx = Transaction.get(txManager, Propagation.Nested, Isolation.ReadCommited, -1) 

// insert a person
import mapperDao._
val person = new Person("Kostas", new Company("Coders limited"))

val inserted = tx { () => insert(PersonEntity, person) } // inserts person, company, in 1 transaction

// print the autogenerated id and the person name
println(s"${inserted.id} ${inserted.name}"))

// now update the company for this person
val company2 = insert(CompanyEntity, Company("Scala Inc"))
val modified = new Person(inserted.name, company2)
val updated = update(PersonEntity, inserted, modified) // no transaction here, but we could do the operation transactionally

// and select it from the database
val selected = select(PersonEntity, updated.id).get

// finally, delete the row
mapperDao.delete(PersonEntity, selected)

// run some queries
val pe=PersonEntity //alias
val people=query(select from pe) // get all
// people is a list of Person with IntId

// fetch only page 2 of all people
val people=query(QueryConfig.pagination(2, 10),select from pe)
// people is a list of Person with IntId

```

## Roadmap ##
  * sqlite driver
  * optimistic locking
  * sum, avg, min, max and for column mappings and groupby in mappings of statistical entities

[Please visit the Wiki for setup instructions & usage documentation](TableOfContents.md)

### MapperDao would like to thank ###

  * [YourKit](http://www.yourkit.com/home/index.jsp) is kindly supporting this open source project with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:

[YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp) and
[YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp).