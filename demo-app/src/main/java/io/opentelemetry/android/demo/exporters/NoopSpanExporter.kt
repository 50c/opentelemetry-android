package io.opentelemetry.android.demo.exporters

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

internal class NoopSpanExporter : SpanExporter {
    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun toString(): String {
        return "NoopSpanExporter{}"
    }

    companion object {
        val instance: SpanExporter = NoopSpanExporter()
    }
}