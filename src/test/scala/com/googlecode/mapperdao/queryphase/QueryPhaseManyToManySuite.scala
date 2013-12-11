//package com.googlecode.mapperdao.queryphase
//
//import org.scalatest.{Matchers, FunSuite}
//import org.junit.runner.RunWith
//import org.scalatest.junit.JUnitRunner
//import com.googlecode.mapperdao.queryphase.model._
//
///**
// * @author kkougios
// */
//@RunWith(classOf[JUnitRunner])
//class QueryPhaseManyToManySuite extends FunSuite with Matchers
//{
//
//	import com.googlecode.mapperdao.CommonEntities._
//
//	val pe = ProductEntity
//	val ae = AttributeEntity
//	val query1 = {
//		import com.googlecode.mapperdao.Query._
//		select from pe where pe.id === 5
//	}
//
//	val query2 = {
//		import com.googlecode.mapperdao.Query._
//		(
//			select from pe
//				join(pe, pe.attributes, ae)
//				where ae.name === "test"
//			)
//	}
//	val maint = InQueryTable(Table(pe.tpe.table), "maint")
//	val attrt = InQueryTable(Table(ae.tpe.table), "a1")
//
//	//	test("where clause") {
//	//		val qp = new QueryPhase
//	//		val q = qp.toQuery(query2)
//	//		q.where should be(WhereValueComparisonClause(Column(attrt, "name"), "=", "?"))
//	//	}
//
//	test("joins") {
//		val qp = new QueryPhase
//		val q = qp.toQuery(query1.queryInfo)
//		val pat = InQueryTable(Table(ProductEntity.attributes.column.linkTable), "a1")
//		val att = InQueryTable(Table(AttributeEntity.tpe.table), "a2")
//		q.joins(0) should be(
//			Join(
//				pat,
//				OnClause(List(Column(maint, "id")), List(Column(pat, "product_id")))
//			)
//		)
//		q.joins(1) should be(
//			Join(
//				att,
//				OnClause(List(Column(att, "id")), List(Column(pat, "attribute_id")))
//			)
//		)
//
//		q.joins.size should be(2)
//	}
//}
