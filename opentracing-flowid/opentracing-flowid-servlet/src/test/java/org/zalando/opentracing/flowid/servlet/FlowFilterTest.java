package org.zalando.opentracing.flowid.servlet;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.mock.MockTracer;
import static io.restassured.RestAssured.*;
import static java.lang.String.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import static org.mockito.Mockito.*;
import org.zalando.opentracing.flowid.Flow;

final class FlowFilterTest {

    private final MockTracer tracer = new MockTracer();
    private final Flow flow = Flow.create(tracer);
    private final FlowFilter filter = new FlowFilter(flow);

    @RegisterExtension
    final JettyExtension jetty = new JettyExtension(filter, tracer, flow);

    private String url() {
        return format("http://localhost:%d/trace", jetty.getPort());
    }

    @Test
    void shouldUseTraceIdAsFlowId() {
        given().
                when()
                .header("traceid", "1")
                .header("spanid", "1")
                .get(url())
                .then()
                .body(equalTo("1"))
                .header("x-flow-id", is(nullValue()));
    }

    @Test
    void shouldUseFlowIdFromBaggage() {
        given().
                when()
                .header("traceid", "1")
                .header("spanid", "1")
                .header("baggage-flow_id", "REcCvlqMSReeo7adheiYFA")
                .get(url())
                .then()
                .body(equalTo("REcCvlqMSReeo7adheiYFA"))
                .header("x-flow-id", is(nullValue()));
    }

    @Test
    void shouldPropagateFlowAsFlowIdHeader() {
        given().
                when()
                .header("traceid", "1")
                .header("spanid", "2")
                .header("x-flow-id", "3")
                .get(url())
                .then()
                .header("x-flow-id", equalTo("3"));
    }

    @Test
    void shouldDenyNonHttpRequest() {
        assertThrows(IllegalArgumentException.class, () ->
                filter.doFilter(mock(ServletRequest.class), mock(HttpServletResponse.class), null));
    }

    @Test
    void shouldDenyNonHttpResponse() {
        assertThrows(IllegalArgumentException.class, () ->
                filter.doFilter(mock(HttpServletRequest.class), mock(ServletResponse.class), null));
    }

}
