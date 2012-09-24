package com.googlecode.mapperdao

/**
 * Every persisted object must mixin with this trait. Client code
 * view this trait only within the constructor() method. Otherwise
 * it is considered mapperdao internal implementation. Exposing the
 * trait to the constructor method could not be avoided (without using
 * runtime proxying), other than that it should be hidden from any client
 * code.
 *
 * @author kostantinos.kougios
 *
 * 17 Jul 2011
 */
protected trait Persisted {

	// to avoid naming conflicts, all these field names start with "mapperDao" 
	@transient
	private[mapperdao] var mapperDaoValuesMap: ValuesMap = null

	// after an orm operation, an object must be discarded. This guards the rule.
	@transient
	private[mapperdao] var mapperDaoDiscarded = false

	// mock objects are used in object graphs with cycles
	@transient
	private[mapperdao] var mapperDaoMock = false
}