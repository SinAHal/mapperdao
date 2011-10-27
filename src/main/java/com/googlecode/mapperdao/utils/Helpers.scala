package com.googlecode.mapperdao.utils

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