/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.view;

import io.opentelemetry.sdk.internal.ThrottlingLogger;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.data.ExemplarData;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.metrics.internal.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.internal.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarFilter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Aggregation that selects the specified default based on instrument.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class DefaultAggregation implements Aggregation, AggregatorFactory {

  private static final Aggregation INSTANCE = new DefaultAggregation();

  public static Aggregation getInstance() {
    return INSTANCE;
  }

  private static final ThrottlingLogger logger =
      new ThrottlingLogger(Logger.getLogger(DefaultAggregation.class.getName()));

  private DefaultAggregation() {}

  /**
   * 通过InstrumentDescriptor中保存的InstrumentType，生成具体的Aggregation对象
   */
  private static Aggregation resolve(InstrumentDescriptor instrument, boolean withAdvice) {
    switch (instrument.getType()) {
      case COUNTER:
      case UP_DOWN_COUNTER:
      case OBSERVABLE_COUNTER:    // COUNTER异步的方式
      case OBSERVABLE_UP_DOWN_COUNTER:    // UP_DOWN_COUNTER异步的方式
        return SumAggregation.getInstance();
      case HISTOGRAM:
        if (withAdvice && instrument.getAdvice().getExplicitBucketBoundaries() != null) {
          return ExplicitBucketHistogramAggregation.create(
              instrument.getAdvice().getExplicitBucketBoundaries());
        }
        return ExplicitBucketHistogramAggregation.getDefault();
      case OBSERVABLE_GAUGE:
        return LastValueAggregation.getInstance();
    }
    logger.log(Level.WARNING, "Unable to find default aggregation for instrument: " + instrument);
    return DropAggregation.getInstance();
  }

  @Override
  public <T extends PointData, U extends ExemplarData> Aggregator<T, U> createAggregator(
      InstrumentDescriptor instrumentDescriptor, ExemplarFilter exemplarFilter) {
    /*
     * 首先通过resolve方法从InstrumentDescriptor中存储的InstrumentType类型来创建具体的Aggregation
     * 然后再调用具体的Aggregation的createAggregator方法
     *   COUNTER: SumAggregation
     *   UP_DOWN_COUNTER: SumAggregation
     *   OBSERVABLE_COUNTER: SumAggregation
     *   OBSERVABLE_UP_DOWN_COUNTER: SumAggregation
     *   HISTOGRAM:ExplicitBucketHistogramAggregation
     *   OBSERVABLE_GAUGE:LastValueAggregation
     */
    return ((AggregatorFactory) resolve(instrumentDescriptor, /* withAdvice= */ true))
        .createAggregator(instrumentDescriptor, exemplarFilter);
  }

  @Override
  public boolean isCompatibleWithInstrument(InstrumentDescriptor instrumentDescriptor) {
    // This should always return true
    return ((AggregatorFactory) resolve(instrumentDescriptor, /* withAdvice= */ false))
        .isCompatibleWithInstrument(instrumentDescriptor);
  }

  @Override
  public String toString() {
    return "DefaultAggregation";
  }
}
