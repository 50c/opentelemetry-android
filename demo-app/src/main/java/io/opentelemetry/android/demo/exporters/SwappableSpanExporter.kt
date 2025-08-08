package io.opentelemetry.android.demo.exporters

import io.opentelemetry.android.demo.TAG
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.javaClass
import android.util.Log as AndroidLog

//val TAG = "SwappableExporter"


class SwappableSpanExporter(delegate: SpanExporter) : SpanExporter {

    private val holder: AtomicReference<SpanExporter> = AtomicReference(delegate)
    private val flushBeforeShutdown: Boolean = false

    fun swap(newDelegate: SpanExporter){
        val oldExporter = holder.get()
        val isSameInstance = oldExporter === newDelegate
        AndroidLog.d(TAG, "Swapping SpanExporter (same instance: $isSameInstance) ${oldExporter.javaClass.name} with: ${newDelegate.javaClass.name} ...")
        holder.set(newDelegate)
        AndroidLog.d(TAG, "Swapping SpanExporter. Shutdown old exporter: ${oldExporter.javaClass.name} ...")
        if( !flushBeforeShutdown ) {
            AndroidLog.d(TAG, "old SpanExporter: ${oldExporter.javaClass.name}.shutdown()")
            oldExporter.shutdown()
            return
        }
        oldExporter.flush().whenComplete {
            AndroidLog.d(TAG, "old SpanExporter: ${oldExporter.javaClass.name}.shutdown()")
            oldExporter.shutdown()
        }
    }

    override fun export(spans: Collection<SpanData?>): CompletableResultCode? {
        return holder.get().export(spans)
    }

    override fun flush(): CompletableResultCode? {
        return holder.get().flush()
    }

    override fun shutdown(): CompletableResultCode? {
        return holder.get().shutdown()
    }

}

class SwappableLogRecordExporter(delegate: LogRecordExporter) : LogRecordExporter {

    private val holder: AtomicReference<LogRecordExporter> = AtomicReference(delegate)
    private val flushBeforeShutdown: Boolean = false

    fun swap(newDelegate: LogRecordExporter){
        val oldExporter = holder.get()
        val isSameInstance = oldExporter === newDelegate
        AndroidLog.d(TAG, "Swapping LogRecordExporter (same instance: $isSameInstance) ${oldExporter.javaClass.name} with: ${newDelegate.javaClass.name} ...")
        holder.set(newDelegate)
        AndroidLog.d(TAG, "Swapping LogRecordExporter. Shutdown old exporter: ${oldExporter.javaClass.name} ...")
        if( !flushBeforeShutdown ) {
            AndroidLog.d(TAG, "old LogRecordExporter: ${oldExporter.javaClass.name}.shutdown()")
            oldExporter.shutdown()
            return
        }
        oldExporter.flush().whenComplete {
            AndroidLog.d(TAG, "old LogRecordExporter: ${oldExporter.javaClass.name}.shutdown()")
            oldExporter.shutdown()
        }
    }

    override fun export(logs: MutableCollection<LogRecordData>): CompletableResultCode =
        holder.get().export(logs)


    override fun flush(): CompletableResultCode =
        holder.get().flush()

    override fun shutdown(): CompletableResultCode =
        holder.get().shutdown()


}

class SwappableMetricExporter(delegate: MetricExporter) : MetricExporter {

    private val holder: AtomicReference<MetricExporter> = AtomicReference(delegate)
    private val flushBeforeShutdown: Boolean = false

    fun swap(newDelegate: MetricExporter){
        val oldExporter = holder.get()
        val isSameInstance = oldExporter === newDelegate
        AndroidLog.d(TAG, "Swapping MetricExporter (same instance: $isSameInstance) ${oldExporter.javaClass.name} with: ${newDelegate.javaClass.name} ...")
        holder.set(newDelegate)
        AndroidLog.d(TAG, "Swapping MetricExporter. Shutdown old exporter: ${oldExporter.javaClass.name} ...")
        if( !flushBeforeShutdown ) {
            AndroidLog.d(TAG, "old MetricExporter: ${oldExporter.javaClass.name}.shutdown()")
            oldExporter.shutdown()
            return
        }
        oldExporter.flush().whenComplete {
            AndroidLog.d(TAG, "old MetricExporter: ${oldExporter.javaClass.name}.shutdown()")
            oldExporter.shutdown()
        }
    }

    override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality =
        holder.get().getAggregationTemporality(instrumentType)


    override fun export(metrics: MutableCollection<MetricData>): CompletableResultCode
        = holder.get().export(metrics)

    override fun flush(): CompletableResultCode =
        holder.get().flush()

    override fun shutdown(): CompletableResultCode =
        holder.get().shutdown()


}