package ai.botkin.satellite.tracing

import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.contrib.spring.web.client.HttpHeadersCarrier
import io.opentracing.contrib.spring.web.client.RestTemplateSpanDecorator
import io.opentracing.contrib.spring.web.client.TracingRestTemplateInterceptor
import io.opentracing.propagation.Format
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse

class CustomTracingRestTemplateInterceptor(
    val tracer: Tracer,
    val span: Span,
    val spanDecorators: List<RestTemplateSpanDecorator>  = listOf()
): TracingRestTemplateInterceptor(tracer, spanDecorators){


    override fun intercept(
        httpRequest: HttpRequest, body: ByteArray?,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse? {
        var httpResponse: ClientHttpResponse? = null
//        val span = tracer.buildSpan(httpRequest.method.toString())
//            .withTag(Tags.SPAN_KIND.key, Tags.SPAN_KIND_CLIENT)
//            .start()
        tracer.inject(
            span.context(), Format.Builtin.HTTP_HEADERS,
            HttpHeadersCarrier(httpRequest.headers)
        )
        for (spanDecorator in spanDecorators) {
            try {
                spanDecorator.onRequest(httpRequest, span)
            } catch (exDecorator: RuntimeException) {
//                log.error("Exception during decorating span", exDecorator)
            }
        }
        try {
            tracer.activateSpan(span).use { scope ->
                httpResponse = execution.execute(httpRequest, body)
                for (spanDecorator in spanDecorators) {
                    try {
                        spanDecorator.onResponse(httpRequest, httpResponse, span)
                    } catch (exDecorator: RuntimeException) {
//                        log.error("Exception during decorating span", exDecorator)
                    }
                }
            }
        } catch (ex: Exception) {
            for (spanDecorator in spanDecorators) {
                try {
                    spanDecorator.onError(httpRequest, ex, span)
                } catch (exDecorator: RuntimeException) {
//                    log.error("Exception during decorating span", exDecorator)
                }
            }
            throw ex
        } finally {

        }
        return httpResponse
    }
}
