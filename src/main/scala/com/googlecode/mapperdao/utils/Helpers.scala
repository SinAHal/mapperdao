package com.googlecode.mapperdao.utils

import com.googlecode.mapperdao.SurrogateIntId
import com.googlecode.mapperdao.CustomIntId
import com.googlecode.mapperdao.SurrogateLongId
import com.googlecode.mapperdao.CustomLongId
import com.googlecode.mapperdao.NoId
import com.googlecode.mapperdao.Persisted

/**
 * useful methods for real life applications that use
 * mapperdao.
 *
 * @author kostantinos.kougios
 *
 * 26 Oct 2011
 */
object Helpers {

	/**
	 * tests any instance to find out if it is a persisted one
	 *
	 * @param o		the instance of the entity
	 * @return		true if the entity was loaded or inserted or updated, false if it
	 * 				is a plain unlinked instance
	 */
	def isPersisted(o: Any) = o match {
		case p: Persisted if (p.mapperDaoValuesMap != null) => true
		case _ => false
	}

	/**
	 * returns the id of an IntId entity or throws an exception if the entity
	 * is not persisted or not of IntId
	 */
	def intIdOf(o: Any): Int = o match {
		case i: SurrogateIntId => i.id
		case _ => throw new IllegalArgumentException("not an IntId : " + o.toString)
	}

	/**
	 * returns the id of a LongId entity or throws an exception if the entity
	 * is not persisted or not of IntId
	 */
	def longIdOf(o: Any): Long = o match {
		case i: SurrogateLongId => i.id
		case _ => throw new IllegalArgumentException("not an LongId : " + o.toString)
	}

	/**
	 * when loading an NoId entity from the database, the type is T with NoId. If for
	 * some reason we're sure that the entity T is of NoId, we can easily cast it
	 * using this utility method
	 */
	def asNoId[T](t: T) = t.asInstanceOf[T with NoId]
	/**
	 * when loading an IntId entity from the database, the type is T with IntId. If for
	 * some reason we're sure that the entity T is of IntId, we can easily cast it
	 * using this utility method
	 */
	def asSurrogateIntId[T](t: T) = t.asInstanceOf[T with SurrogateIntId]
	def asCustomIntId[T](t: T) = t.asInstanceOf[T with CustomIntId]
	/**
	 * when loading an LongId entity from the database, the type is T with LongId. If for
	 * some reason we're sure that the entity T is of LongId, we can easily cast it
	 * using this utility method
	 */
	def asSurrogateLongId[T](t: T) = t.asInstanceOf[T with SurrogateLongId]
	def asCustomLongId[T](t: T) = t.asInstanceOf[T with CustomLongId]

	/**
	 * merges oldSet and newSet items, keeping all unmodified
	 * items from oldSet and adding all newItems from newSet.
	 * If t1 belongs to oldSet and t1e==t1 belongs to newSet,
	 * then t1 will be retained. This helps with collection
	 * updates, as 2 sets (old and new) can be merged before
	 * instantiating updated entities.
	 *
	 * @param oldSet		the set containing old values
	 * @param newSet		the set, updated with new values
	 * @return				merged set which == newSet but contains
	 * 						instances from oldSet where appropriate.
	 */
	def merge[T](oldSet: Set[T], newSet: Set[T]): Set[T] =
		{
			val intersection = oldSet.intersect(newSet)
			val added = newSet.filterNot(oldSet.contains(_))
			intersection ++ added
		}

	/**
	 * merges the 2 lists with result==newList but result
	 * retaining all instances of oldList that are contained
	 * in newList. This helps with mapperdao collection
	 * updates, provided that all contained instances
	 * impl equals().
	 *
	 * This method also retains the order of the items
	 * (as is in newList) and duplicate items that are
	 * contained both in newList and oldList
	 *
	 * @param oldList		the list of items before the update
	 * @param newList		the list of items after the update,
	 * 						all of them might be new instances
	 * 						but equal() to items in oldList
	 * @return				the merged list, where merged==newList
	 * 						but retains all instances from oldList
	 * 						that are contained in newList
	 */
	def merge[T](oldList: List[T], newList: List[T]): List[T] =
		{
			val ml = new collection.mutable.ArrayBuffer ++ oldList
			newList.map { item =>
				ml.find(_ == item) match {
					case Some(ni) =>
						ml -= ni
						ni
					case None => item
				}
			}
		}
}