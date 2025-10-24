package io.opentelemetry.android.demo

import android.app.Application
import android.util.Log
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.OpenTelemetryRumBuilder
import io.opentelemetry.android.agent.connectivity.EndpointConnectivity
import io.opentelemetry.android.agent.connectivity.HttpEndpointConnectivity
import io.opentelemetry.android.agent.session.SessionConfig
import io.opentelemetry.android.agent.session.SessionIdGenerator
import io.opentelemetry.android.agent.session.SessionStorage
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.demo.exporters.NoopLogRecordExporter
import io.opentelemetry.android.demo.exporters.NoopMetricExporter
import io.opentelemetry.android.demo.exporters.NoopSpanExporter
import io.opentelemetry.android.demo.exporters.SwappableLogRecordExporter
import io.opentelemetry.android.demo.exporters.SwappableMetricExporter
import io.opentelemetry.android.demo.exporters.SwappableSpanExporter
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader
import io.opentelemetry.android.instrumentation.activity.ActivityLifecycleInstrumentation
import io.opentelemetry.android.instrumentation.anr.AnrInstrumentation
import io.opentelemetry.android.instrumentation.common.EventAttributesExtractor
import io.opentelemetry.android.instrumentation.common.ScreenNameExtractor
import io.opentelemetry.android.instrumentation.crash.CrashDetails
import io.opentelemetry.android.instrumentation.crash.CrashReporterInstrumentation
import io.opentelemetry.android.instrumentation.fragment.FragmentLifecycleInstrumentation
import io.opentelemetry.android.instrumentation.network.NetworkAttributesExtractor
import io.opentelemetry.android.instrumentation.network.NetworkChangeInstrumentation
import io.opentelemetry.android.instrumentation.slowrendering.SlowRenderingInstrumentation
import io.opentelemetry.android.internal.services.Services
import io.opentelemetry.android.internal.services.applifecycle.ApplicationStateListener
import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionObserver
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.android.session.SessionPublisher
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.logging.otlp.internal.logs.OtlpStdoutLogRecordExporter
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.common.export.RetryPolicy
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import java.time.Duration
import java.util.Collections.synchronizedList

object TestOpenTelemetryRumInitializer {

    val retryPolicy= RetryPolicy.getDefault().toBuilder()
        .setMaxAttempts(2)
        .setInitialBackoff(Duration.ofMillis(1))
        .build()

    val callTimeout = Duration.ofSeconds(5)
    val connectTimeout = Duration.ofSeconds(5)

    val coroutineScope = CoroutineScope(Dispatchers.IO + CoroutineName("otel-rum-initializer"))

    private val swappableLogRecordExporter: SwappableLogRecordExporter = SwappableLogRecordExporter(NoopLogRecordExporter.instance)
    private val swappableSpanExporter: SwappableSpanExporter = SwappableSpanExporter(NoopSpanExporter.instance)
    private val swappableMetricExporter: SwappableMetricExporter = SwappableMetricExporter(NoopMetricExporter.instance)

    /**
     * Opinionated [OpenTelemetryRum] initialization.
     *
     * @param application Your android app's application object.
     * @param endpointBaseUrl The base endpoint for exporting all your signals.
     * @param endpointHeaders These will be added to each signal export request.
     * @param spanEndpointConnectivity Span-specific endpoint configuration.
     * @param logEndpointConnectivity Log-specific endpoint configuration.
     * @param metricEndpointConnectivity Metric-specific endpoint configuration.
     * @param rumConfig Configuration used by [OpenTelemetryRumBuilder].
     * @param sessionConfig The session configuration, which includes inactivity timeout and maximum lifetime durations.
     * @param activityTracerCustomizer Tracer customizer for [ActivityLifecycleInstrumentation].
     * @param activityNameExtractor Name extractor for [ActivityLifecycleInstrumentation].
     * @param fragmentTracerCustomizer Tracer customizer for [FragmentLifecycleInstrumentation].
     * @param fragmentNameExtractor Name extractor for [FragmentLifecycleInstrumentation].
     * @param anrAttributesExtractors Attribute extractors for [AnrInstrumentation].
     * @param crashAttributesExtractors Attribute extractors for [CrashReporterInstrumentation].
     * @param networkChangeAttributesExtractors Attribute extractors for [NetworkChangeInstrumentation].
     * @param slowRenderingDetectionPollInterval Slow rendering detection interval for [SlowRenderingInstrumentation].
     */
    @JvmStatic
    fun initialize(
        application: Application,
        endpointBaseUrl: String,
        endpointHeaders: Map<String, String> = emptyMap(),
        spanEndpointConnectivity: EndpointConnectivity =
            HttpEndpointConnectivity.forTraces(
                endpointBaseUrl,
                endpointHeaders,
            ),
        logEndpointConnectivity: EndpointConnectivity =
            HttpEndpointConnectivity.forLogs(
                endpointBaseUrl,
                endpointHeaders,
            ),
        metricEndpointConnectivity: EndpointConnectivity =
            HttpEndpointConnectivity.forMetrics(
                endpointBaseUrl,
                endpointHeaders,
            ),
        rumConfig: OtelRumConfig = OtelRumConfig(),
        sessionConfig: SessionConfig = SessionConfig.withDefaults(),
        activityTracerCustomizer: ((Tracer) -> Tracer)? = null,
        activityNameExtractor: ScreenNameExtractor? = null,
        fragmentTracerCustomizer: ((Tracer) -> Tracer)? = null,
        fragmentNameExtractor: ScreenNameExtractor? = null,
        anrAttributesExtractors: List<EventAttributesExtractor<Array<StackTraceElement>>> = emptyList(),
        crashAttributesExtractors: List<EventAttributesExtractor<CrashDetails>> = emptyList(),
        networkChangeAttributesExtractors: List<NetworkAttributesExtractor> = emptyList(),
        slowRenderingDetectionPollInterval: Duration? = null,
    ): OpenTelemetryRum {
        configureInstrumentation(
            activityTracerCustomizer,
            activityNameExtractor,
            fragmentTracerCustomizer,
            fragmentNameExtractor,
            anrAttributesExtractors,
            crashAttributesExtractors,
            networkChangeAttributesExtractors,
            slowRenderingDetectionPollInterval,
        )


        val rumBuilder = OpenTelemetryRum
            .builder(application, rumConfig)
            .setSessionProvider(createSessionProvider(application, sessionConfig))
            .addSpanExporterCustomizer {
                swappableSpanExporter
            }
            .addLogRecordExporterCustomizer {
                swappableLogRecordExporter
            }
            .addMetricExporterCustomizer {
                swappableMetricExporter
            }

        coroutineScope.launch {
            while (true) {
                val rotationPeriod= Duration.ofSeconds(2)
                Log.d(TAG, "Rotating exporters after ${rotationPeriod.toSeconds()} seconds ...")
                delay(rotationPeriod) // rotate every 2 seconds
                Log.d(TAG, "Rotating exporters ...")
                swappableLogRecordExporter.swap(createLogRecordExporter(logEndpointConnectivity))
                swappableSpanExporter.swap(createSpanExporter(spanEndpointConnectivity))
                swappableMetricExporter.swap(createMetricExporter(metricEndpointConnectivity))
                Log.d(TAG, "Rotating exporters done.")
            }
        }

        return rumBuilder.build()

        //rotate periodically

    }

    private fun createMetricExporter(
        metricEndpointConnectivity: EndpointConnectivity
    ): MetricExporter = OtlpGrpcMetricExporter
        .builder()
        .setRetryPolicy(retryPolicy)
        .setTimeout(callTimeout)
        .setConnectTimeout(connectTimeout)
        .setEndpoint(metricEndpointConnectivity.getUrl())
        .setHeaders(metricEndpointConnectivity::getHeaders)
        .build()

    private fun createLogRecordExporter(
        logEndpointConnectivity: EndpointConnectivity
    ): LogRecordExporter {
        //return OtlpStdoutLogRecordExporter.builder().build()
        return OtlpGrpcLogRecordExporter
            .builder()
            .setRetryPolicy(retryPolicy)
            .setTimeout(callTimeout)
            .setConnectTimeout(connectTimeout)
            .setEndpoint(logEndpointConnectivity.getUrl())
            .setHeaders(logEndpointConnectivity::getHeaders)
            .build()
    }

    private fun createSpanExporter(
        spanEndpointConnectivity: EndpointConnectivity
    ): SpanExporter {
        return LoggingSpanExporter.create()
        return OtlpGrpcSpanExporter
            .builder()
            .setRetryPolicy(retryPolicy)
            .setTimeout(callTimeout)
            .setConnectTimeout(connectTimeout)
            .setEndpoint(spanEndpointConnectivity.getUrl())
            .setHeaders(spanEndpointConnectivity::getHeaders)
            .build()
    }

    private fun createSessionProvider(
        application: Application,
        sessionConfig: SessionConfig,
    ): SessionProvider {
        val timeoutHandler = SessionIdTimeoutHandler(sessionConfig)
        Services.get(application).appLifecycle.registerListener(timeoutHandler)
        return SessionManager.create(timeoutHandler, sessionConfig)
    }

    private fun configureInstrumentation(
        activityTracerCustomizer: ((Tracer) -> Tracer)?,
        activityNameExtractor: ScreenNameExtractor?,
        fragmentTracerCustomizer: ((Tracer) -> Tracer)?,
        fragmentNameExtractor: ScreenNameExtractor?,
        anrAttributesExtractors: List<EventAttributesExtractor<Array<StackTraceElement>>>,
        crashAttributesExtractors: List<EventAttributesExtractor<CrashDetails>>,
        networkChangeAttributesExtractors: List<NetworkAttributesExtractor>,
        slowRenderingDetectionPollInterval: Duration?,
    ) {
        val activityLifecycleInstrumentation =
            getInstrumentation<ActivityLifecycleInstrumentation>()
        if (activityTracerCustomizer != null) {
            activityLifecycleInstrumentation?.setTracerCustomizer(activityTracerCustomizer)
        }
        if (activityNameExtractor != null) {
            activityLifecycleInstrumentation?.setScreenNameExtractor(activityNameExtractor)
        }

        val fragmentLifecycleInstrumentation =
            getInstrumentation<FragmentLifecycleInstrumentation>()
        if (fragmentTracerCustomizer != null) {
            fragmentLifecycleInstrumentation?.setTracerCustomizer(fragmentTracerCustomizer)
        }
        if (fragmentNameExtractor != null) {
            fragmentLifecycleInstrumentation?.setScreenNameExtractor(fragmentNameExtractor)
        }

        if (anrAttributesExtractors.isNotEmpty()) {
            val anrInstrumentation = getInstrumentation<AnrInstrumentation>()
            for (extractor in anrAttributesExtractors) {
                anrInstrumentation?.addAttributesExtractor(extractor)
            }
        }

        if (crashAttributesExtractors.isNotEmpty()) {
            val crashInstrumentation = getInstrumentation<CrashReporterInstrumentation>()
            for (extractor in crashAttributesExtractors) {
                crashInstrumentation?.addAttributesExtractor(extractor)
            }
        }

        if (networkChangeAttributesExtractors.isNotEmpty()) {
            val networkChangeInstrumentation = getInstrumentation<NetworkChangeInstrumentation>()
            for (extractor in networkChangeAttributesExtractors) {
                networkChangeInstrumentation?.addAttributesExtractor(extractor)
            }
        }

        if (slowRenderingDetectionPollInterval != null) {
            getInstrumentation<SlowRenderingInstrumentation>()?.setSlowRenderingDetectionPollInterval(
                slowRenderingDetectionPollInterval,
            )
        }
    }

    private inline fun <reified T : AndroidInstrumentation> getInstrumentation(): T? =
        AndroidInstrumentationLoader.getInstrumentation(T::class.java)
}

internal class SessionIdTimeoutHandler(
    private val clock: Clock,
    private val sessionBackgroundInactivityTimeout: kotlin.time.Duration,
) : ApplicationStateListener {
    @Volatile
    private var timeoutStartNanos: Long = 0

    @Volatile
    private var state = State.FOREGROUND

    // for testing
    internal constructor(sessionConfig: SessionConfig) : this(
        Clock.getDefault(),
        sessionConfig.backgroundInactivityTimeout,
    )

    override fun onApplicationForegrounded() {
        state = State.TRANSITIONING_TO_FOREGROUND
    }

    override fun onApplicationBackgrounded() {
        state = State.BACKGROUND
    }

    fun hasTimedOut(): Boolean {
        // don't apply sessionId timeout to apps in the foreground
        if (state == State.FOREGROUND) {
            return false
        }
        val elapsedTime = clock.nanoTime() - timeoutStartNanos
        return elapsedTime >= sessionBackgroundInactivityTimeout.inWholeNanoseconds
    }

    fun bump() {
        timeoutStartNanos = clock.nanoTime()

        // move from the temporary transition state to foreground after the first span
        if (state == State.TRANSITIONING_TO_FOREGROUND) {
            state = State.FOREGROUND
        }
    }

    private enum class State {
        FOREGROUND,
        BACKGROUND,

        /** A temporary state representing the first event after the app has been brought back.  */
        TRANSITIONING_TO_FOREGROUND,
    }
}

internal class SessionManager(
    private val clock: Clock = Clock.getDefault(),
    private val sessionStorage: SessionStorage = SessionStorage.InMemory(),
    private val timeoutHandler: SessionIdTimeoutHandler,
    private val idGenerator: SessionIdGenerator = SessionIdGenerator.DEFAULT,
    private val maxSessionLifetime: kotlin.time.Duration,
) : SessionProvider,
    SessionPublisher {
    // TODO: Make thread safe / wrap with AtomicReference?
    private var session: Session = Session.NONE
    private val observers = synchronizedList(ArrayList<SessionObserver>())

    init {
        sessionStorage.save(session)
    }

    override fun addObserver(observer: SessionObserver) {
        observers.add(observer)
    }

    override fun getSessionId(): String {
        // value will never be null
        var newSession = session

        if (sessionHasExpired() || timeoutHandler.hasTimedOut()) {
            val newId = idGenerator.generateSessionId()

            // TODO FIXME: This is not threadsafe -- if two threads call getSessionId()
            // at the same time while timed out, two new sessions are created
            // Could require SessionStorage impls to be atomic/threadsafe or
            // do the locking in this class?

            newSession = Session.DefaultSession(newId, clock.now())
            sessionStorage.save(newSession)
        }

        timeoutHandler.bump()

        // observers need to be called after bumping the timer because it may
        // create a new span
        if (newSession != session) {
            val previousSession = session
            session = newSession
            observers.forEach {
                it.onSessionEnded(previousSession)
                it.onSessionStarted(session, previousSession)
            }
        }
        return session.getId()
    }

    private fun sessionHasExpired(): Boolean {
        val elapsedTime = clock.now() - session.getStartTimestamp()
        return elapsedTime >= maxSessionLifetime.inWholeNanoseconds
    }

    companion object {
        @JvmStatic
        fun create(
            timeoutHandler: SessionIdTimeoutHandler,
            sessionConfig: SessionConfig,
        ): SessionManager =
            SessionManager(
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = sessionConfig.maxLifetime,
            )
    }
}