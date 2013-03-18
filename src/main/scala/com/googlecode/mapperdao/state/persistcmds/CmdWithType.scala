package com.googlecode.mapperdao.state.persistcmds

import com.googlecode.mapperdao._

/**
 * @author kostantinos.kougios
 *
 *         18 Dec 2012
 */
trait CmdWithType[ID, T] extends PersistCmd {
	val tpe: Type[ID, T]
}