package io.opentelemetry.android.demo.exporters

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter


internal class NoopLogRecordExporter : LogRecordExporter {
    override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    companion object {
        val instance: LogRecordExporter = NoopLogRecordExporter()
    }
}

