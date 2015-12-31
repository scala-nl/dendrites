/**
  */
package org

import java.util.Properties

/** @author garystruthers
  *
  */
package object gs {

  /** Extract case class elements into a Map
    *
    * @example [[org.gs.http.caseClassToGetQuery]]
    *
    * @param cc case class (Product is supertype)
    * @return map of field names and values
    */
  def ccToMap(cc: Product) = cc.getClass.getDeclaredFields.foldLeft(Map[String, Any]()) {
    (a, f) =>
      f.setAccessible(true) // to get private fields
      a + (f.getName -> f.get(cc))
  }

  /** Does the indexed case class field have desired type?
    *
    * @example [[org.gs.examples.account.actor.AccountBalanceRetrieverSpec]]
    *
    * @param case class (Product is supertype)
    * @param ele field element
    * @param theType type to match
    * @return true if element has theType
    */
  def isElementEqual(p: Product, ele: Int, theType: Any): Boolean = {
    p match {
      case _ if (p.productArity >= ele && p.productElement(ele) == theType) => true
      case _ => false
    }
  }

  /** Read Properties file
    *
    * @param filename must be in src/main/resources
    * @return Properties object
    */
  def loadProperties(filename: StringBuilder): Properties = {
    val prop = new Properties()
    filename.insert(0, '/')
    prop.load(getClass.getResourceAsStream(filename.toString()))
    prop
  }

}