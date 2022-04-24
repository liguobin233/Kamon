/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.sttp

import kamon.testkit.TestSpanReporter
import org.scalatest.{ BeforeAndAfterAll, OptionValues }
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.client3.{ asStringAlways, basicRequest, HttpURLConnectionBackend, Identity, SttpBackend, UriContext }

import scala.concurrent.duration.DurationInt

class SttpSpec extends AnyWordSpec with Matchers
  with TestSpanReporter
  with Eventually
  with BeforeAndAfterAll
  with OptionValues {

  var backend: SttpBackend[Identity, Any] = _

  override def beforeAll(): Unit = {
    backend = HttpURLConnectionBackend()
  }

  override def afterAll(): Unit = {
    backend.close()
  }


  "the sttp client3" should {
    "basicRequest support span" in {
      val ret = basicRequest
        .response(asStringAlways)
        .get(uri"http://httpbin.org/ip").send(backend)
        .body
      println(ret)
      eventually(timeout(2.seconds)) {
        println(testSpanReporter().spans())
        val span = testSpanReporter().nextSpan()
        span.map { s =>
          s.operationName shouldBe "/hello/{param1}"
        }
      }
    }
  }
}
