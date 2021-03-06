/**
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.github.garyaiki.dendrites.algebird

import com.twitter.algebird.AveragedValue
import org.scalatest.FlatSpecLike
import org.scalatest.Matchers._
import com.github.garyaiki.dendrites.aggregator.mean
import com.github.garyaiki.dendrites.fixtures.TestValuesBuilder

/**
  * @author Gary Struthers
  *
  */
class AveragedSpec extends FlatSpecLike with TestValuesBuilder {

  "An AveragedValue of BigDecimals" should "be near their mean" in {
    val av = avg(bigDecimals)
    assert(av.count === bigDecimals.size)
    val m = mean(bigDecimals)
    assert(av.value === (m.right.get.toDouble +- 0.005))
  }

  it should "be summable in a Sequence" in {
    val avg0 = avg(bigDecimals)
    val avg1 = avg(bigDecimals2)
    val avgs = Vector[AveragedValue](avg0, avg1)
    val m = mean(bigDecimals ++ bigDecimals2)
    val avgSum = sumAverageValues(avgs)
    assert(avgSum.value === (m.right.get.toDouble +- 0.005))
  }

  "An AveragedValue of BigInts" should "be near their mean" in {
    val av = avg(bigInts)
    val m = mean(bigInts)
    assert(av.count === bigInts.size)
    assert(av.value === (m.right.get.toDouble +- 0.5))
  }

  it should "be summable in a Sequence" in {
    val avg0 = avg(bigInts)
    val avg1 = avg(bigInts2)
    val m = mean(bigInts ++ bigInts2)
    val avgs = Vector[AveragedValue](avg0, avg1)
    val avgSum = sumAverageValues(avgs)
    assert(avgSum.value === (m.right.get.toDouble +- 0.5))
  }

  "An AveragedValue of Doubles" should "be near their mean" in {
    val av = avg(doubles)
    val m = mean(doubles)
    assert(av.count === doubles.size)
    assert(av.value === (m.right.get +- 0.005))
  }

  it should "be summable in a Sequence" in {
    val avg0 = avg(doubles)
    val avg1 = avg(doubles2)
    val m = mean(doubles ++ doubles2)
    val avgs = Vector[AveragedValue](avg0, avg1)
    val avgSum = sumAverageValues(avgs)
    assert(avgSum.value === (m.right.get.toDouble +- 0.5))
  }

  "An AveragedValue of Floats" should "be near their mean" in {
    val av = avg(floats)
    val m = mean(floats)
    assert(av.count === floats.size)
    assert(av.value === (m.right.get.toDouble +- 0.005))
  }

  it should "be summable in a Sequence" in {
    val avg0 = avg(floats)
    val avg1 = avg(floats2)
    val m = mean(floats ++ floats2)
    val avgs = Vector[AveragedValue](avg0, avg1)
    val avgSum = sumAverageValues(avgs)
    assert(avgSum.value === (m.right.get.toDouble +- 0.5))
  }

  "An AveragedValue of Ints" should "be near their mean" in {
    val av = avg(ints)
    val m = mean(ints)
    assert(av.count === ints.size)
    assert(av.value === (m.right.get.toDouble +- 0.5))
  }

  it should "be summable in a Sequence" in {
    val avg0 = avg(ints)
    val avg1 = avg(ints2)
    val m = mean(ints ++ ints2)
    val avgs = Vector[AveragedValue](avg0, avg1)
    val avgSum = sumAverageValues(avgs)
    assert(avgSum.value === (m.right.get.toDouble +- 0.5))
  }

  "An AveragedValue of Longs" should "be near their mean" in {
    val av = avg(longs)
    val m = mean(ints)
    assert(av.count === ints.size)
    assert(av.value === (m.right.get.toDouble +- 0.5))
  }

  it should "be summable in a Sequence" in {
    val avg0 = avg(longs)
    val avg1 = avg(longs2)
    val m = mean(longs ++ longs2)
    val avgs = Vector[AveragedValue](avg0, avg1)
    val avgSum = sumAverageValues(avgs)
    assert(avgSum.value === (m.right.get.toDouble +- 0.5))
  }
}
