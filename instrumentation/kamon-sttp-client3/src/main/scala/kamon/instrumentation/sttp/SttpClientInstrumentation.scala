package kamon.instrumentation.sttp

import kamon.Kamon
import kamon.instrumentation.http._
import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler
import kamon.util.CallingThreadExecutionContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import sttp.client3
import sttp.client3.{ Identity, RequestT, Response }
import sttp.model.Header

import kamon.trace.Span

import scala.concurrent.Future
import scala.util.{ Failure, Success }

/**
  *
  * @author guobin.li@ascendex.io
  * @version 1.0,2022/4/20
  */
class SttpClientInstrumentation extends InstrumentationBuilder {

  // intercept RequestT send
  onType("sttp.client3.HttpURLConnectionBackend")
    .intercept(method("send"), classOf[HttpURLConnectionBackendInterceptor])

  onType("sttp.client3.asynchttpclient.AsyncHttpClientBackend")
    .intercept(method("send"), classOf[AsyncHttpClientBackendInterceptor])
}


object SttpClientInstrumentation {

  type Request[T, -R] = RequestT[Identity, T, R]


  @volatile var httpClientInstrumentation: HttpClientInstrumentation = rebuildHttpClientInstrumentation()

  Kamon.onReconfigure(_ => httpClientInstrumentation = rebuildHttpClientInstrumentation())

  private[sttp] def rebuildHttpClientInstrumentation(): HttpClientInstrumentation = {
    val httpClientConfig = Kamon.config().getConfig("kamon.instrumentation.sttp.client3")
    httpClientInstrumentation = HttpClientInstrumentation.from(httpClientConfig, "sttp.client3")
    httpClientInstrumentation
  }

  //  def getHandler[S <: Either[_,_]](request: Request[S, Any]): RequestHandler[Request[S, Any]] = {
  //    httpClientInstrumentation.createHandler[Request[S, Any]](toRequestBuilder[S, Any](request), Kamon.currentContext())
  //  }

  def getHandler[T, R](request: Request[T, R], span: Span): RequestHandler[Request[T, R]] = {
    httpClientInstrumentation.createHandler[Request[T, R]](toRequestBuilder[T, R](request),
      Kamon.currentContext().withEntry(Span.Key, span))
  }

  def toResponseBuilder[T](response: Response[T]): HttpMessage.Response = new HttpMessage.Response {
    override def statusCode: Int = response.code.code
  }

  def toRequestBuilder[T, R](httpRequest: Request[T, R]): HttpMessage.RequestBuilder[Request[T, R]] =
    new RequestReader[T, R] with HttpMessage.RequestBuilder[Request[T, R]] {

      private var _extraHeaders = List.empty[Header]

      override val request: Request[T, R] = httpRequest

      override def write(header: String, value: String): Unit =
        _extraHeaders = Header(header, value) :: _extraHeaders

      override def build(): Request[T, R] = request.copy(headers = request.headers ++ _extraHeaders)
    }

  //  def handleResponse[E, A, S <: Either[E, A]](response: Response[S], ex: Throwable, handler: RequestHandler[Request[S, Object]]): client3.Response[S] = {
  //    if (ex != null) {
  //      handler.span.fail(ex)
  //    }
  //    try {
  //
  //      handler.processResponse(toResponseBuilder(response))
  //
  //    } catch {
  //      case e: Exception =>
  //        handler.span.fail(e)
  //        throw e
  //    } finally {
  //      handler.span.finish()
  //    }
  //
  //    response
  //  }

  def handleResponse[T, R](response: Response[T], handler: RequestHandler[Request[T, R]]): client3.Response[T] = {
    try {

      handler.processResponse(toResponseBuilder(response))

    } catch {
      case e: Exception =>
        handler.span.fail(e)
        throw e
    } finally {
      handler.span.finish()
    }

    response
  }

  def handleResponse[T, R](response: Future[Response[T]], handler: RequestHandler[Request[T, R]]): Future[client3.Response[T]] = {
    response.onComplete {
      case Success(res) => handler.processResponse(toResponseBuilder(res))
      case Failure(t) => handler.span.fail(t).finish()
    }(CallingThreadExecutionContext)

    response
  }
}