package org.zalando.opentracing.flowid;

import io.opentracing.Span;
import io.opentracing.Tracer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

@Slf4j
@AllArgsConstructor
final class DefaultFlow implements Flow {

    private final Extractor extractor = new CompositeExtractor(
            new BaggageExtractor(),
            new HeaderExtractor(),
            new TraceExtractor()
    );

    private final Tracer tracer;
    private final FlowListener listener;

    @Override
    public void readFrom(final UnaryOperator<String> reader) {
        final Span span = activeSpan();

        final Optional<FlowId> now = extractor.extract(span, reader);
        final Optional<FlowId> later = extractor.extract(span);

        final Optional<String> nowId = now.map(FlowId::getValue);
        final Optional<String> laterId = later.map(FlowId::getValue);

        if (nowId.equals(laterId)) {
            later.ifPresent(flowId -> {
                listener.onRead(span, flowId);
            });
        } else {
            now.ifPresent(flowId -> {
                bag(span, flowId);
                listener.onRead(span, flowId);
            });
        }
    }

    private void bag(final Span span, final FlowId flowId) {
        span.setBaggageItem(Baggage.FLOW_ID, flowId.getValue());
    }

    @Override
    public String currentId() {
        return extractor.extract(activeSpan())
                .map(FlowId::getValue)
                .orElseThrow(IllegalStateException::new);
    }

    @Override
    public void writeTo(final BiConsumer<String, String> writer) {
        extractor.extract(activeSpan())
                .map(FlowId::getValue)
                .ifPresent(value ->
                        writer.accept(Header.FLOW_ID, value));
    }

    @Override
    public <T> T write(final BiFunction<String, String, T> writer) {
        return writer.apply(Header.FLOW_ID, currentId());
    }

    private Span activeSpan() {
        @Nullable final Span span = tracer.activeSpan();

        if (span == null) {
            throw new IllegalStateException("No active span found");
        }

        return span;
    }

}
