package com.googlecode.mapperdao.utils

import org.springframework.transaction.PlatformTransactionManager
import com.googlecode.mapperdao.jdbc.Transaction.Isolation
import com.googlecode.mapperdao.jdbc.Transaction.Propagation
import com.googlecode.mapperdao.jdbc.Transaction
import com.googlecode.mapperdao.DeclaredIds

/**
 * CRUD with TransactionalCRUD will run CRUD methods within transactions
 *
 * Please look at :
 *
 * https://code.google.com/p/mapperdao/wiki/CRUDDaos
 * https://code.google.com/p/mapperdao/wiki/Transactions
 *
 * T is the entity type, i.e. Product
 * PC is the key type, i.e. SurrogateIntId.
 * ID is the type of the key, i.e. Int or String
 */
trait TransactionalCRUD[ID, T] extends CRUD[ID, T] {
	protected val txManager: PlatformTransactionManager

	/**
	 * override this to change type of transaction that will occur and it's timeout
	 */
	protected def prepareTransaction: Transaction = Transaction.get(txManager, Propagation.Nested, Isolation.ReadCommited, -1)

	override def retrieve(pk: ID): Option[T with DeclaredIds[ID]] = prepareTransaction {
		() =>
			super.retrieve(pk)
	}

	override def create(t: T): T with DeclaredIds[ID] = prepareTransaction {
		() =>
			super.create(t)
	}

	override def update(t: T with DeclaredIds[ID]): T with DeclaredIds[ID] = prepareTransaction {
		() =>
			super.update(t)
	}

	override def update(oldValue: T with DeclaredIds[ID], newValue: T): T with DeclaredIds[ID] = prepareTransaction {
		() =>
			super.update(oldValue, newValue)
	}

	override def merge(t: T, id: ID): T with DeclaredIds[ID] = prepareTransaction {
		() =>
			super.merge(t, id)
	}

	override def delete(t: T with DeclaredIds[ID]): T = prepareTransaction {
		() =>
			super.delete(t)
	}

	override def delete(id: ID): Unit = prepareTransaction {
		() =>
			super.delete(id)
	}
}
