package kamon.instrumentation.sttp

import kamon.context.Context
import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler
import kamon.instrumentation.sttp.SttpClientInstrumentation.Request
import kamon.Kamon
import kamon.trace.{ Identifier, Span }
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{ Argument, SuperCall }
import sttp.capabilities.Effect
import sttp.client3.{ Identity, RequestT, Response }

import java.util.concurrent.Callable
import scala.concurrent.Future

/**
  *
  * @author guobin.li@ascendex.io
  * @version 1.0,2022/4/21
  */
class AsyncHttpClientBackendInterceptor

object AsyncHttpClientBackendInterceptor {

  def send[T, R >: Any with Effect[Future]](@Argument(0) arg: Any, @SuperCall superCall: Callable[Future[Response[T]]]): Future[Response[T]] = {
    var span: Span = null
    val traceKey: Context.Key[String] = Context.key[String]("parentTraceId", "undefined")
    var traceIdVal: Option[String] = None
    var requestHandler: RequestHandler[Request[T, R]] = null
    val response = arg match {
      case request: RequestT[Identity, T, R] =>
        val zcall = superCall.call()
        requestHandler = SttpClientInstrumentation.getHandler[T, R](request, Kamon.currentSpan())
        val traceId = request.headers.find(header => header.name == "traceid")
        val parentSpanId = request.headers.find(header => header.name == "spanid")
        val parentSpanIdVal = parentSpanId.map(_.value)

        traceIdVal = traceId.map(_.value)

        val spanBuilder = Kamon.spanBuilder(request.method.method)
          .tag("protocol", "http2->1")
          .tag("component", "sttp.client3")
          .tag("http.method", request.method.method)
          .tag("path", s"${request.uri.path.mkString("/")}")

        traceIdVal.foreach(tid => {
          spanBuilder
            .traceId(Identifier.Scheme.Single.traceIdFactory.from(tid))
            .setParentId(parentSpanIdVal)
        })
        span = spanBuilder.start()
        Kamon.runWithContext(Kamon.currentContext().withEntry(Span.Key, span)) {
          zcall
        }
      case x =>
        superCall.call()
    }

    Kamon.runWithSpan(Kamon.currentSpan(), finishSpan = false) {
      Kamon.runWithContextEntry(traceKey, traceIdVal.getOrElse("undefined")) {
        SttpClientInstrumentation.handleResponse[T, R](response, requestHandler)
      }
    }
  }

}

