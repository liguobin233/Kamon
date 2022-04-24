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

package kamon.instrumentation.sttp;

import kamon.Kamon;
import kamon.context.Storage;
import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler;
import kamon.trace.Span;
import kamon.trace.SpanBuilder;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import sttp.client3.RequestT;
import sttp.client3.Response;


public class RequestSendAdvice {

    @Advice.OnMethodEnter()
    public static RequestHandler<RequestT> enter(@Advice.Argument(value = 0, readOnly = false) RequestT request,
                                                 @Advice.Local("scope") Storage.Scope scope) {
        RequestHandler<RequestT> handler = SttpClientInstrumentation.getHandler(request);
        String path = request.uri().toString();
        SpanBuilder spanBuilder = Kamon.spanBuilder(path)
                .tag("protocol", "http2->1")
                .tag("component", "sttp.client3")
                .tag("http.method", request.method().toString())
                .tag("path", path);

        if (!Kamon.currentSpan().isEmpty()) {
            spanBuilder.asChildOf(Kamon.currentSpan());
        }
        Span span = spanBuilder.start().takeSamplingDecision();
        scope = Kamon.storeContext(Kamon.currentContext().withEntry(Span.Key(), span));
        request = handler.request();
        return handler;

    }

    @Advice.OnMethodExit
    public static <T> void exit(@Advice.Enter RequestHandler<RequestT> handler,
                                @Advice.Return(readOnly = false) Response<T> response,
                                @Advice.Local("scope") Storage.Scope scope) {
        try {

            handler.processResponse(SttpClientInstrumentation.toResponseBuilder(response));

        } catch (Exception e) {
            handler.span().fail(e);
            throw e;
        } finally {
            handler.span().finish();
            scope.close();
        }
    }
}
