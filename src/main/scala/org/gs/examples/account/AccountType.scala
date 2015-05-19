/**
  */
package org.gs.examples.account

/** @author garystruthers
  *
  */
sealed trait AccountType extends Product
case object Checking extends AccountType
case object Savings extends AccountType
case object MoneyMarket extends AccountType

final case class GetAccountBalances(id: Long)

