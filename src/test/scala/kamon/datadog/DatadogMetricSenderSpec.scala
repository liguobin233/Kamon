/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.datadog

import java.time.Instant

import com.typesafe.config.{ Config, ConfigFactory }
import kamon.Kamon
import kamon.datadog.DatadogAgentReporter.PacketBuffer
import kamon.metric._
import kamon.tag.TagSet
import kamon.testkit.Reconfigure
import org.scalatest.{ Matchers, WordSpec }

class DatadogMetricSenderSpec extends WordSpec with Matchers with Reconfigure {
  reconfigure =>

  class TestBuffer extends PacketBuffer {

    val lst = scala.collection.mutable.Map.empty[String, String]

    override def flush(): Unit = {}
    override def appendMeasurement(
      key:             String,
      measurementData: String
    ): Unit = {
      lst += (key -> measurementData)
    }
  }

  "the DataDogMetricSender" should {
    "send counter metrics" in AgentReporter(new TestBuffer(), ConfigFactory.parseString("kamon.environment.tags.env = staging").withFallback(Kamon.config())) {
      case (buffer, reporter) =>

        val now = Instant.now()
        reporter.reportPeriodSnapshot(
          PeriodSnapshot.apply(
            now.minusMillis(1000),
            now,
            MetricSnapshot.ofValues[Long](
              "test.counter",
              "test",
              Metric.Settings.ForValueInstrument(MeasurementUnit.none, java.time.Duration.ZERO),
              Instrument.Snapshot.apply(TagSet.of("tag1", "value1"), 0L) :: Nil) :: Nil,
            Nil,
            Nil,
            Nil,
            Nil
          )
        )

        buffer.lst should have size 1
        buffer.lst should contain("test.counter" -> "0|c|#service:kamon-application,env:staging,tag1:value1")
    }

    "filter out blacklisted tags" in AgentReporter(new TestBuffer(), ConfigFactory.parseString(
      """
        |kamon.datadog.environment-tags.exclude = [env]
        |kamon.environment.tags.env = staging
        |""".stripMargin).withFallback(Kamon.config())) {
      case (buffer, reporter) =>

        val now = Instant.now()
        reporter.reportPeriodSnapshot(
          PeriodSnapshot.apply(
            now.minusMillis(1000),
            now,
            MetricSnapshot.ofValues[Long](
              "test.counter",
              "test",
              Metric.Settings.ForValueInstrument(MeasurementUnit.none, java.time.Duration.ZERO),
              Instrument.Snapshot.apply(TagSet.of("tag1", "value1"), 0L) :: Nil) :: Nil,
            Nil,
            Nil,
            Nil,
            Nil
          )
        )

        buffer.lst should have size 1
        buffer.lst should contain("test.counter" -> "0|c|#service:kamon-application,tag1:value1")
    }

    "filter other tags" in AgentReporter(new TestBuffer(), ConfigFactory.parseString(
      """
        |kamon.datadog.environment-tags.exclude = [ "tag*" ]
        |kamon.environment.tags.env = staging
        |""".stripMargin).withFallback(Kamon.config())) {
      case (buffer, reporter) =>

        val now = Instant.now()
        reporter.reportPeriodSnapshot(
          PeriodSnapshot.apply(
            now.minusMillis(1000),
            now,
            MetricSnapshot.ofValues[Long](
              "test.counter",
              "test",
              Metric.Settings.ForValueInstrument(MeasurementUnit.none, java.time.Duration.ZERO),
              Instrument.Snapshot.apply(TagSet.of("tag1", "value1").withTag("tag2", "value2").withTag("otherTag", "otherValue"), 0L) :: Nil) :: Nil,
            Nil,
            Nil,
            Nil,
            Nil
          )
        )

        buffer.lst should have size 1
        buffer.lst should contain("test.counter" -> "0|c|#service:kamon-application,env:staging,tag1:value1,tag2:value2,otherTag:otherValue")
    }

  }

  def AgentReporter[A, B <: PacketBuffer](buffer: B, config: Config)(f: (B, DatadogAgentReporter) => A): A = {
    Kamon.reconfigure(config)

    // Overriding a class for testing is an anti-pattern. But it's the best I could do
    val reporter = new DatadogAgentReporter(
      DatadogAgentReporter
        .readConfiguration(
          Kamon.config()
        ).copy(packetBuffer = buffer)
    )
    f(buffer, reporter)
  }
}
