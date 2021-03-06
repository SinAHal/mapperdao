# Simple mappings #

## Domain Class ##

Lets start with a simple entity which is self contained in 1 table. The entity is JobPosition and it contains information about
the name, start and end time of this job position along with it's id (there is an
other wiki on [how to configure auto generated id's](AutoGenerated.md))

This sample entity is immutable. Though mapper dao promotes immutability, mutable entities can be persisted too.

```
// mapperdao supports joda DateTime, Calendar's and Date's can be used as well

class JobPosition(val id: Int, val name: String, val start: DateTime, val end: DateTime, val rank: Int) {
	// this can have any arbitrary methods, anything
	// that is not mapped won't be persisted
	def daysDiff = (end.getMillis - start.getMillis) / (3600 * 24)

	// same goes for fields. The following will not be persisted
	val whatever = 5
}
```

There is nothing special about this class, nothing that couples it with mapperdao. With mapperdao there is no coupling between domain
classes and the orm layer.

We will soon create our table and mapping, but lets see what we will be able to do when everything is ready:

```
// assuming we got our mapperDao and queryDao instances configured:

// insert a JobPosition
val date = DateTime.now
val inserted=mapperDao.insert(JobPositionEntity, new JobPosition(5, "Developer", date, date, 10))
// inserted equals to JobPosition and can be used for further operations

// select a JobPosition
val loaded = mapperDao.select(JobPositionEntity, 5).get
// loaded == inserted

// delete a JobPosition
mapperDao.delete(JobPositionEntity, loaded)

// query for JobPosition's. Queries resemble sql query syntax.
// So, first lets import the query DSL
import com.googlecode.mapperdao.Query._

// an alias to the entity's table...
val jpe=JobPositionEntity

// ... and some queries

// selects all job positions with name="Scala Developer", returns List[JobPosition]
val l1 = queryDao.query(select from jpe where jpe.name === "Scala Developer")

// selects all job positions with name like "%eveloper%", returns List[JobPosition]
val l2 = (select from jpe where jpe.name like "%eveloper%").toList(queryDao)

```


## Table ##

Now lets create our table. DDL for postgresql follows:

```
create table JobPosition (
	id int not null,
	name varchar(100) not null,
	start timestamp with time zone,
	"end" timestamp with time zone,
	rank int not null,
	primary key (id)
)
```

To keep things simple, we named our table after our domain class and our columns after our fields. But this is not necessary, the table
can have any name, same goes for the columns. The default naming convention maps a class to `class.getSimpleName` but the mapping
code can change this to our preferred table.

## Mapping ##

It's now time to map our domain class to our database table:

```
import com.googlecode.mapperdao._

object JobPositionEntity extends Entity[Int,NaturalIntId,JobPosition] {
	// We will now map the columns to the entity.
	// Each column is followed by a function JobPosition=>T, that
	// returns the value of the property for that column.
	val id = key("id") to (_.id) // this is the primary key and maps to JobPosition.id
	val name = column("name") to (_.name) // _.name : JobPosition => String . Function that maps the column to the value of the object
	val start = column("start") to (_.start) // _.start : JobPosition=>DateTime
	val end = column("end") to (_.end)
	val rank = column("rank") to (_.rank)

	// a function from ValuesMap=>JobPosition that constructs the object.
	def constructor(implicit m) = new JobPosition(id, name, start, end, rank) 
		with Stored
}
```

Lets review the mapping:

```
import com.googlecode.mapperdao._
```
This imports mapperdao classes, i.e. SimpleEntity

```
object JobPositionEntity extends Entity[Int,NaturalIntId,JobPosition]
```

This starts the declaration of JobPositionEntity which extends Entity. And the mapping is for JobPosition which has a NaturalIntId. There
are all short of available [Id traits](IDS.md) that declare natural or surrogate ids, composite id's and so on and the user can create
their own id traits.

```
val id = key("id") to (_.id)
```

This maps the primary key of the table. The table column is "id" and it maps to JobPosition.id. It can be written as:

```
val id = key("id") to ( (j:JobPosition)=> j.id )
```

Same goes for the rest of the columns. They map to strings, dates, ints etc:
```
val name = column("name") to (_.name)
val start = column("start") to (_.start)
val end = column("end") to (_.end)
val rank = column("rank") to (_.rank)
```

Finally, mapperdao needs a way to construct new instances of your domain classes. The constructor does this:

```
def constructor(implicit m:ValuesMap) = new JobPosition(id, name, start, end, rank) 
	with Stored
```

The constructor instantiates JobPosition using a ValuesMap. A ValuesMap contains all the values as they are loaded from the database.
The fields declared previously can be used to get the value with the correct type, i.e. `m(id)` or `m.int(id)` (Int) , `m(name)` (String)
and so on. But notice that m is implicit and it is used to implicitly convert id,name,... to their type. Hence we can
do `new JobPosition(id, name,....` .


Mapperdao needs to know the state of the entity when it was loaded from the database. This way it can later on test it for changes. That's why, the constructor must construct an instance of the entity with Persisted. The `Persisted` trait takes care of state changes. This way, when later on the entity is updated, mapperdao knows what changed. Persisted is not otherwise visible to the user of mapperdao.

Note: The JobPositionEntity object should better be placed inside the dao for JobPosition and in the dao's companion object. As a good practice, please don't place mapping objects into companion objects of the entity because this effectively couples your domain with mapperdao. Please see [creating dao's wiki](CRUDDaos.md)