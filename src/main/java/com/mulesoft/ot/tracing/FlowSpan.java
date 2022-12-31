package com.mulesoft.ot.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FlowSpan implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(FlowSpan.class);

    private final String flowName;
    private final Span span;
    private boolean ending = false;
    private final Map<String, Span> childSpans = new ConcurrentHashMap<>();
    private boolean ended = false;

    public FlowSpan(String flowName, Span span) {
        this.flowName = flowName;
        this.span = span;
    }

    public Span getSpan() {
        return span;
    }

    public Span addProcessorSpan(String location, SpanBuilder spanBuilder) {
        if (ending || ended)
            throw new UnsupportedOperationException(
                    "Flow: " + flowName + ", span: " + (ended ? "has ended" : "is ending"));
        Span span = spanBuilder.setParent(Context.current().with(getSpan())).startSpan();
        childSpans.put(location, span);
        log.debug("Add span: {}", span.getSpanContext().getSpanId());
        return span;
    }

    public void endProcessorSpan(String location, Consumer<Span> spanUpdater, Instant endTime) {
        if ((!ending || ended) && childSpans.containsKey(location)) {
            Span removed = childSpans.remove(location);
            if (spanUpdater != null){
                spanUpdater.accept(removed);
            }
            log.debug("Removed span: {}", span.getSpanContext().getSpanId());
            removed.end(endTime);
        }
    }

    public void end(Instant endTime) {
        ending = true;
        childSpans.forEach((location, span) -> span.end(endTime));
        span.end(endTime);
        ended = true;
    }
}
