package com.googlecode.mapperdao

/**
 * configuration for running queries using multiple
 * threads.
 *
 * Warning: multi threaded queries ignore transactions. Entities
 * retrieved using a multi-threaded query might contain
 * out-of-transaction data.
 *
 * @author kostantinos.kougios
 *
 * 6 May 2012
 */
abstract class MultiThreadedConfig {
	val runInParallel: Boolean
	val inGroupsOf: Int
}

object MultiThreadedConfig {
	object Single extends MultiThreadedConfig {
		val runInParallel = false
		val inGroupsOf = -1
	}

	object Multi extends MultiThreadedConfig {
		val runInParallel = true
		val inGroupsOf = 8
	}
}