/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.export;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.internal.DaemonThreadFactory;
import io.opentelemetry.sdk.internal.ThrowableUtil;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.internal.JcTools;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of the {@link SpanProcessor} that batches spans exported by the SDK then pushes
 * them to the exporter pipeline.
 *
 * <p>All spans reported by the SDK implementation are first added to a synchronized queue (with a
 * {@code maxQueueSize} maximum size, if queue is full spans are dropped). Spans are exported either
 * when there are {@code maxExportBatchSize} pending spans or {@code scheduleDelayNanos} has passed
 * since the last export finished.
 */
public final class BatchSpanProcessor implements SpanProcessor {

  private static final Logger logger = Logger.getLogger(BatchSpanProcessor.class.getName());

  private static final String WORKER_THREAD_NAME = BatchSpanProcessor.class.getSimpleName() + "_WorkerThread";
  private static final AttributeKey<String> SPAN_PROCESSOR_TYPE_LABEL = AttributeKey.stringKey("processorType");
  private static final AttributeKey<Boolean> SPAN_PROCESSOR_DROPPED_LABEL = AttributeKey.booleanKey("dropped");
  private static final String SPAN_PROCESSOR_TYPE_VALUE = BatchSpanProcessor.class.getSimpleName();

  private final Worker worker;
  private final AtomicBoolean isShutdown = new AtomicBoolean(false);

  /**
   * Returns a new Builder for {@link BatchSpanProcessor}.
   *
   * @param spanExporter the {@link SpanExporter} to which the Spans are pushed.
   * @return a new {@link BatchSpanProcessorBuilder}.
   * @throws NullPointerException if the {@code spanExporter} is {@code null}.
   */
  public static BatchSpanProcessorBuilder builder(SpanExporter spanExporter) {
    return new BatchSpanProcessorBuilder(spanExporter);
  }

  BatchSpanProcessor(SpanExporter spanExporter, MeterProvider meterProvider, long scheduleDelayNanos, int maxQueueSize, int maxExportBatchSize, long exporterTimeoutNanos) {
    this.worker = new Worker(spanExporter, meterProvider, scheduleDelayNanos, maxExportBatchSize, exporterTimeoutNanos, JcTools.newFixedSizeQueue(maxQueueSize));
    Thread workerThread = new DaemonThreadFactory(WORKER_THREAD_NAME).newThread(worker);
    workerThread.start();
  }

  @Override
  public void onStart(Context parentContext, ReadWriteSpan span) {}

  @Override
  public boolean isStartRequired() {
    return false;
  }

  @Override
  public void onEnd(ReadableSpan span) {
    // 如果Span为空或不采样，则直接返回，一般默认是全采样
    if (span == null || !span.getSpanContext().isSampled()) {
      return;
    }
    worker.addSpan(span);
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }

  @Override
  public CompletableResultCode shutdown() {
    if (isShutdown.getAndSet(true)) {
      return CompletableResultCode.ofSuccess();
    }
    return worker.shutdown();
  }

  @Override
  public CompletableResultCode forceFlush() {
    return worker.forceFlush();
  }

  // Visible for testing
  List<SpanData> getBatch() {
    return worker.batch;
  }

  // Visible for testing
  Queue<ReadableSpan> getQueue() {
    return worker.queue;
  }

  @Override
  public String toString() {
    return "BatchSpanProcessor{"
        + "spanExporter="
        + worker.spanExporter
        + ", scheduleDelayNanos="
        + worker.scheduleDelayNanos
        + ", maxExportBatchSize="
        + worker.maxExportBatchSize
        + ", exporterTimeoutNanos="
        + worker.exporterTimeoutNanos
        + '}';
  }

  // Worker is a thread that batches multiple spans and calls the registered SpanExporter to export
  // the data.
  private static final class Worker implements Runnable {

    private final LongCounter processedSpansCounter;
    private final Attributes droppedAttrs;
    private final Attributes exportedAttrs;

    private final SpanExporter spanExporter;
    // 默认5s
    private final long scheduleDelayNanos;
    // 默认大小为512
    private final int maxExportBatchSize;
    // 默认为30s
    private final long exporterTimeoutNanos;

    private long nextExportTime;

    private final Queue<ReadableSpan> queue;
    // When waiting on the spans queue, exporter thread sets this atomic to the number of more
    // spans it needs before doing an export. Writer threads would then wait for the queue to reach
    // spansNeeded size before notifying the exporter thread about new entries.
    // Integer.MAX_VALUE is used to imply that exporter thread is not expecting any signal. Since
    // exporter thread doesn't expect any signal initially, this value is initialized to
    // Integer.MAX_VALUE.
    private final AtomicInteger spansNeeded = new AtomicInteger(Integer.MAX_VALUE);
    private final BlockingQueue<Boolean> signal;
    private final AtomicReference<CompletableResultCode> flushRequested = new AtomicReference<>();
    private volatile boolean continueWork = true;
    private final ArrayList<SpanData> batch;

    private Worker(SpanExporter spanExporter,
        MeterProvider meterProvider,
        long scheduleDelayNanos,
        int maxExportBatchSize,
        long exporterTimeoutNanos,
        Queue<ReadableSpan> queue) {
      this.spanExporter = spanExporter;
      this.scheduleDelayNanos = scheduleDelayNanos;
      this.maxExportBatchSize = maxExportBatchSize;
      this.exporterTimeoutNanos = exporterTimeoutNanos;
      this.queue = queue;
      this.signal = new ArrayBlockingQueue<>(1);
      /*
       * Meter默认是SdkMeter（在SdkMeterProvider构造方法中被设置）, meterProvider默认是SdkMeterProvider
       * build调用获取的内容是SdkMeterProvider构造方法设置的: new SdkMeter(sharedState, instrumentationLibraryInfo, registeredReaders)
       * 这里设置了scope的名称为：io.opentelemetry.sdk.trace
       */
      Meter meter = meterProvider.meterBuilder("io.opentelemetry.sdk.trace").build();
      // 这里首先设置了Meter的名称，通过ofLongs相当于做了一个类型转换，设置description和unit
      // unit字段是用来描述指定某个遥测数据的单位的
      meter.gaugeBuilder("queueSize")  // 通过SdkMeter构建返回SdkDoubleGaugeBuilder
          .ofLongs()    // 调用SdkDoubleGaugeBuilder构建返回SdkLongGaugeBuilder
          .setDescription("The number of items queued") // 这里是调用AbstractInstrumentBuilder的setDescription方法
          .setUnit("1") // 这里是调用AbstractInstrumentBuilder的setUnit方法
          // 构建异步instrument，调用SdkLongGaugeBuilder的buildWithCallback
          // 这里Attributes.of构造的其实是：processorType：batchSpanProcessor
          .buildWithCallback(result -> result.record(queue.size(), Attributes.of(SPAN_PROCESSOR_TYPE_LABEL, SPAN_PROCESSOR_TYPE_VALUE)));
      processedSpansCounter = meter.counterBuilder("processedSpans")  // 构建LongCounterBuilder
          .setUnit("1")
          .setDescription("The number of spans processed by the BatchSpanProcessor. [dropped=true if they were dropped due to high throughput]")
          .build();
      droppedAttrs = Attributes.of(SPAN_PROCESSOR_TYPE_LABEL, SPAN_PROCESSOR_TYPE_VALUE, SPAN_PROCESSOR_DROPPED_LABEL, true);
      exportedAttrs = Attributes.of(SPAN_PROCESSOR_TYPE_LABEL, SPAN_PROCESSOR_TYPE_VALUE, SPAN_PROCESSOR_DROPPED_LABEL, false);
      this.batch = new ArrayList<>(this.maxExportBatchSize);
    }

    // 在调用Span.end方法时会将span添加到阻塞队列中
    private void addSpan(ReadableSpan span) {
      if (!queue.offer(span)) {
        // 这里是调用SdkLongCounter的add方法
        processedSpansCounter.add(1, droppedAttrs);
      } else {
        if (queue.size() >= spansNeeded.get()) {
          signal.offer(true);
        }
      }
    }

    @Override
    public void run() {  // 默认5s执行一次
      updateNextExportTime();

      while (continueWork) {
        if (flushRequested.get() != null) {
          flush();
        }
        // 将Span数据从queue中poll出来，添加到batch中
        JcTools.drain(queue, maxExportBatchSize - batch.size(), span -> batch.add(span.toSpanData()));

        if (batch.size() >= maxExportBatchSize || System.nanoTime() >= nextExportTime) {
          exportCurrentBatch();
          // 更新nextExportTime时间为5s后
          updateNextExportTime();
        }
        if (queue.isEmpty()) {
          try {
            long pollWaitTime = nextExportTime - System.nanoTime();
            if (pollWaitTime > 0) {
              spansNeeded.set(maxExportBatchSize - batch.size());
              signal.poll(pollWaitTime, TimeUnit.NANOSECONDS);
              spansNeeded.set(Integer.MAX_VALUE);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
        }
      }
    }

    private void flush() {
      int spansToFlush = queue.size();
      while (spansToFlush > 0) {
        ReadableSpan span = queue.poll();
        assert span != null;
        batch.add(span.toSpanData());
        spansToFlush--;
        if (batch.size() >= maxExportBatchSize) {
          exportCurrentBatch();
        }
      }
      exportCurrentBatch();
      CompletableResultCode flushResult = flushRequested.get();
      if (flushResult != null) {
        flushResult.succeed();
        flushRequested.set(null);
      }
    }

    private void updateNextExportTime() {
      nextExportTime = System.nanoTime() + scheduleDelayNanos;
    }

    private CompletableResultCode shutdown() {
      CompletableResultCode result = new CompletableResultCode();

      CompletableResultCode flushResult = forceFlush();
      flushResult.whenComplete(
          () -> {
            continueWork = false;
            CompletableResultCode shutdownResult = spanExporter.shutdown();
            shutdownResult.whenComplete(
                () -> {
                  if (!flushResult.isSuccess() || !shutdownResult.isSuccess()) {
                    result.fail();
                  } else {
                    result.succeed();
                  }
                });
          });

      return result;
    }

    private CompletableResultCode forceFlush() {
      CompletableResultCode flushResult = new CompletableResultCode();
      // we set the atomic here to trigger the worker loop to do a flush of the entire queue.
      if (flushRequested.compareAndSet(null, flushResult)) {
        signal.offer(true);
      }
      CompletableResultCode possibleResult = flushRequested.get();
      // there's a race here where the flush happening in the worker loop could complete before we
      // get what's in the atomic. In that case, just return success, since we know it succeeded in
      // the interim.
      return possibleResult == null ? CompletableResultCode.ofSuccess() : possibleResult;
    }

    private void exportCurrentBatch() {
      if (batch.isEmpty()) {
        return;
      }

      try {
        CompletableResultCode result = spanExporter.export(Collections.unmodifiableList(batch));
        result.join(exporterTimeoutNanos, TimeUnit.NANOSECONDS);
        if (result.isSuccess()) {
          // 统计导出数量
          processedSpansCounter.add(batch.size(), exportedAttrs);
        } else {
          logger.log(Level.FINE, "Exporter failed");
        }
      } catch (Throwable t) {
        ThrowableUtil.propagateIfFatal(t);
        logger.log(Level.WARNING, "Exporter threw an Exception", t);
      } finally {
        batch.clear();
      }
    }
  }
}
