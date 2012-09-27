package com.googlecode.mapperdao.utils
import com.googlecode.mapperdao._

/**
 * provides CRUD methods for entities with IntId
 *
 * @see CRUD
 */
trait SurrogateIntIdCRUD[T] extends CRUD[Int, SurrogateIntId, T]

/**
 * provides CRUD methods for entities with LongId
 *
 * @see CRUD
 */
trait SurrogateLongIdCRUD[T] extends CRUD[Long, SurrogateLongId, T]

/**
 * these mixin traits add querying methods to a dao. Please see the All trait
 */
trait SurrogateIntIdAll[T] extends All[SurrogateIntId, T]
trait SurrogateLongIdAll[T] extends All[SurrogateLongId, T]

trait NaturalIntIdCRUD[T] extends CRUD[Int, NaturalIntId, T]
trait NaturalLongIdCRUD[T] extends CRUD[Long, NaturalLongId, T]
trait NaturalStringIdCRUD[T] extends CRUD[String, NaturalStringId, T]

trait NaturalStringIdAll[T] extends All[NaturalStringId, T]
trait NaturalIntIdAll[T] extends All[NaturalIntId, T]
trait NaturalLongIdAll[T] extends All[NaturalLongId, T]
