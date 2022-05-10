/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation.http

import kamon.Kamon
import kamon.instrumentation.tag.TagKeys
import kamon.metric.InstrumentGroup
import kamon.metric.MeasurementUnit.information
import kamon.tag.TagSet
import org.slf4j.LoggerFactory

object GrpcHttpServerMetrics {

  private val _logger = LoggerFactory.getLogger("kamon.instrumentation.http.GrpcHttpServerMetrics")

  val CompletedRequests = Kamon.counter(
    name = "grpc.server.requests",
    description = "Number of completed requests per status code"
  )

  val ActiveRequests = Kamon.rangeSampler(
    name = "grpc.server.request.active",
    description = "Number of requests being processed simultaneously at any point in time"
  )

  val RequestSize = Kamon.histogram(
    name = "grpc.server.request.size",
    description = "Request size distribution (including headers and body) for all requests received by the server",
    unit = information.bytes
  )

  val ResponseSize = Kamon.histogram(
    name = "grpc.server.response.size",
    description = "Response size distribution (including headers and body) for all responses served by the server",
    unit = information.bytes
  )

  /**
    * Holds all metric instruments required to track the behavior of a gRPC server.
    */
  class GrpcHttpServerInstruments(commonTags: TagSet) extends InstrumentGroup(commonTags) {
    val requestsInformational = register(CompletedRequests, TagKeys.HttpStatusCode, "1xx")
    val requestsSuccessful = register(CompletedRequests, TagKeys.HttpStatusCode, "2xx")
    val requestsRedirection = register(CompletedRequests, TagKeys.HttpStatusCode, "3xx")
    val requestsClientError = register(CompletedRequests, TagKeys.HttpStatusCode, "4xx")
    val requestsServerError = register(CompletedRequests, TagKeys.HttpStatusCode, "5xx")

    val activeRequests = register(ActiveRequests)
    val requestSize = register(RequestSize)
    val responseSize = register(ResponseSize)

    /**
      * Increments the appropriate response counter depending on the the status code.
      */
    def countCompletedRequest(statusCode: Int): Unit = {
      if (statusCode >= 200 && statusCode <= 299)
        requestsSuccessful.increment()
      else if (statusCode >= 500 && statusCode <= 599)
        requestsServerError.increment()
      else if (statusCode >= 400 && statusCode <= 499)
        requestsClientError.increment()
      else if (statusCode >= 300 && statusCode <= 399)
        requestsRedirection.increment()
      else if (statusCode >= 100 && statusCode <= 199)
        requestsInformational.increment()
      else {
        _logger.warn("Unknown HTTP status code {} found when recording gRPC server metrics", statusCode.toString)
      }
    }
  }

  /**
    * Creates a new HttpServer.Metrics instance with the provided component, interface and port tags.
    */
  def of(component: String, interface: String, port: Int): GrpcHttpServerInstruments =
    new GrpcHttpServerInstruments(
      TagSet.builder()
        .add(TagKeys.Component, component)
        .add(TagKeys.Interface, interface)
        .add(TagKeys.Port, port)
        .add(TagKeys.RpcSystem, "akka.grpc")
        .build()
    )
}
