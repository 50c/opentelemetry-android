package io.opentelemetry.android.demo.exporters

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.export.MemoryMode
import io.opentelemetry.sdk.metrics.Aggregation
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter

class NoopMetricExporter
/**
 * Create a [NoopMetricExporter] with aggregationTemporality, aggregation and memory mode.
 */(
    private val aggregationTemporality: AggregationTemporality,
    private val aggregation: Aggregation,
    private val memoryMode: MemoryMode
) : MetricExporter {
    override fun export(metrics: Collection<MetricData>): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun getDefaultAggregation(instrumentType: InstrumentType): Aggregation {
        return aggregation
    }

    override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality {
        return aggregationTemporality
    }

    override fun getMemoryMode(): MemoryMode {
        return memoryMode
    }

    companion object {
        val instance: MetricExporter = NoopMetricExporter(AggregationTemporality.CUMULATIVE, Aggregation.defaultAggregation(), MemoryMode.IMMUTABLE_DATA)
    }
}