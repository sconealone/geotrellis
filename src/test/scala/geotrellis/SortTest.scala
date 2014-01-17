package geotrellis

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

import scala.util.Random

class SortTest extends FunSuite with ShouldMatchers {
  test("should be sorted") {
    val N = 100
    val randomArray = (for (i <- 0 until N) yield Random.nextInt).toArray
    val sortedArray = randomArray.sorted
    // By transitivity, just check that for each pair of neighbouring elements
    // the left element is <= the right element
    for (i <- 0 until N-1)
      sortedArray(i) should be <= sortedArray(i+1)
  }
}
