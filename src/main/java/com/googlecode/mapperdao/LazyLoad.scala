package com.googlecode.mapperdao

/**
 * @author kostantinos.kougios
 *
 * 18 Apr 2012
 */
case class LazyLoad(all: Boolean = true)

object LazyLoad {
	// dont lazy load anything
	val none = LazyLoad(false)
	val all = LazyLoad(all = true)
}