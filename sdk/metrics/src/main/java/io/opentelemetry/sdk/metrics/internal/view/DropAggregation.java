/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.view;

import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.data.ExemplarData;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.metrics.internal.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.internal.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarFilter;

/**
 * Configuration representing no aggregation.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 *
 *
 * 一种指标聚合策略，用于指标处理管道中。在OpenTelemetry中，如果某种指标的数据流量非常大，我们可能不希望将所有的指标数据点都保存下来，
 * 而是希望根据某种策略来决定丢弃一部分数据点。DropAggregation就是用来实现这种功能的。
 *
 * 当使用DropAggregation时，OpenTelemetry会根据配置的策略丢弃一部分的指标数据点。这种策略可以用来防止内存溢出、
 * 防止网络带宽的使用或者是为了简化数据模型。
 */
public final class DropAggregation implements Aggregation, AggregatorFactory {

  private static final Aggregation INSTANCE = new DropAggregation();

  public static Aggregation getInstance() {
    return INSTANCE;
  }

  private DropAggregation() {}

  @Override
  @SuppressWarnings("unchecked")
  public <T extends PointData, U extends ExemplarData> Aggregator<T, U> createAggregator(
      InstrumentDescriptor instrumentDescriptor, ExemplarFilter exemplarFilter) {
    return (Aggregator<T, U>) Aggregator.drop();
  }

  @Override
  public boolean isCompatibleWithInstrument(InstrumentDescriptor instrumentDescriptor) {
    return true;
  }

  @Override
  public String toString() {
    return "DropAggregation";
  }
}
