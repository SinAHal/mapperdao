package com.googlecode.mapperdao.utils
import com.googlecode.mapperdao.SurrogateIntId
import com.googlecode.mapperdao.LongId
import com.googlecode.mapperdao.StringId

/**
 * provides CRUD methods for entities with IntId
 *
 * @see CRUD
 */
trait IntIdCRUD[T] extends CRUD[Int, SurrogateIntId, T]

/**
 * provides CRUD methods for entities with LongId
 *
 * @see CRUD
 */
trait LongIdCRUD[T] extends CRUD[Long, LongId, T]

trait StringIdCRUD[T] extends CRUD[String, StringId, T]

/**
 * these mixin traits add querying methods to a dao. Please see the All trait
 */
trait IntIdAll[T] extends All[SurrogateIntId, T]
trait LongIdAll[T] extends All[LongId, T]
trait StringIdAll[T] extends All[StringId, T]
