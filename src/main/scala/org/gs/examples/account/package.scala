/**
  */
package org.gs.examples

import scala.collection.immutable.Set
/** @author garystruthers
  *
  */
package object account {

  type AccBalances[A] = Option[List[(Long, A)]]
  def AccBalancesVec[A](xs: AccBalances[A]*) = Vector(xs: _*)
  
  val accountTypes: Set[AccountType] = Set(Checking, Savings, MoneyMarket)
  val accountSavingsType: Set[AccountType] = Set(Savings)

  def isAccBalances[A](e: Any): Boolean = e.isInstanceOf[AccBalances[A]]

  def isAccountType(e: Any): Boolean = e.isInstanceOf[AccountType]

}