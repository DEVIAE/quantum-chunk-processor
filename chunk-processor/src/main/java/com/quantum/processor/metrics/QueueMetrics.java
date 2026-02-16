package com.quantum.processor.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class QueueMetrics {

    private final AtomicLong queueDepth = new AtomicLong(0);
    private final AtomicLong dlqDepth = new AtomicLong(0);
    private final MeterRegistry meterRegistry;

    public QueueMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        Gauge.builder("amq.queue.depth", queueDepth, AtomicLong::get)
                .description("Current depth of the chunk processing queue")
                .tag("queue", "quantum.file.chunks")
                .register(meterRegistry);

        Gauge.builder("amq.dlq.depth", dlqDepth, AtomicLong::get)
                .description("Current depth of the dead letter queue")
                .tag("queue", "DLQ.quantum.file.chunks")
                .register(meterRegistry);

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Gauge.builder("jvm.heap.usage.ratio", memoryBean, bean ->
                        (double) bean.getHeapMemoryUsage().getUsed() / bean.getHeapMemoryUsage().getMax())
                .description("JVM heap usage ratio")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${processor.metrics.queue-poll-interval:15000}")
    public void pollQueueMetrics() {
        // In production, this would query AMQ's management API
        // to get actual queue depths via Jolokia or the AMQ management console
    }

    public void updateQueueDepth(long depth) {
        queueDepth.set(depth);
    }

    public void updateDlqDepth(long depth) {
        dlqDepth.set(depth);
    }
}
