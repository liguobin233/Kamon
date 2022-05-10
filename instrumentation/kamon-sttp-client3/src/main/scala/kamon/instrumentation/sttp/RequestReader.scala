package kamon.instrumentation.sttp

import kamon.instrumentation.http.HttpMessage
import sttp.client3._

/**
  * @author guobin.li@ascendex.io
  * @version 1.0, 2022/4/20
  */
private[sttp] trait RequestReader[T, R] extends HttpMessage.Request {

  def request: RequestT[Identity, T, R]

  override def url: String = request.uri.toString

  override def path: String = request.uri.path.mkString("/")

  override def method: String = request.method.method

  override def host: String = request.uri.authority.map(_.host).orNull

  override def port: Int = request.uri.authority.flatMap(_.port).getOrElse(80)

  override def read(header: String): Option[String] = request.headers(header).headOption

  override def readAll(): Map[String, String] = {
    val builder = Map.newBuilder[String, String]
    request.headers.foreach(h => builder += (h.name -> h.value))
    builder.result()
  }
}