package kamon.instrumentation.akka.grpc

import kamon.Kamon
import kamon.trace.SpanBuilder
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice


class AkkaGrpcClientInstrumentation[I, O] extends InstrumentationBuilder {
  onType("akka.grpc.internal.ScalaUnaryRequestBuilder")
    .advise(method("invoke"), AkkaGrpcClientInstrumentation)


}

object AkkaGrpcClientInstrumentation {
  @Advice.OnMethodEnter()
  def enter[I](@Advice.Argument(0) request: I): Unit = {
    val requestStr = request.toString
    val serviceName = requestStr.substring(0, requestStr.indexOf("("))
    var spanBuilder: SpanBuilder = null
    if (Kamon.currentSpan().isEmpty) {
      Kamon.spanBuilder("grpc " + serviceName).start()
    }
    Kamon.currentSpan()
      .tagMetrics("component", "akka.grpc.client")
      .tagMetrics("rpc.system", "grpc")
      .tagMetrics("rpc.service", serviceName)
      .takeSamplingDecision()
  }
}
