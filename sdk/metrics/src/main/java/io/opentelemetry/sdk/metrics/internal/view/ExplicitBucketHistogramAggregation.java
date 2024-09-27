/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.view;

import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.data.ExemplarData;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.metrics.internal.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.internal.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.internal.aggregator.DoubleExplicitBucketHistogramAggregator;
import io.opentelemetry.sdk.metrics.internal.aggregator.ExplicitBucketHistogramUtils;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarFilter;
import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarReservoir;
import java.util.List;

/**
 * Explicit bucket histogram aggregation configuration.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 *
 * ExplicitBucketHistogramAggregation允许用户明确指定Histogram的桶（buckets），即数值的分布范围，这对于需要精细控制数据分布收集的用户非常有用。
 * Histogram的聚合策略决定了如何将原始数据聚合成可报告的统计信息
 * ExplicitBucketHistogramAggregation允许用户自定义每个桶的范围，这样可以根据业务需求精确地捕获数据的分布情况
 * 例如，如果业务场景中需要了解请求延迟的具体分布，包括哪些请求在特定时间内完成，哪些请求超时等，通过ExplicitBucketHistogramAggregation可以设置不同的桶来捕获这些信息。
 *
 * ExplicitBucketHistogramAggregation还支持与OpenTelemetry的其他功能集成，如与Jaeger进行集成，将收集到的性能数据发送到Jaeger进行分析和可视化，
 * 帮助开发人员深入了解应用程序的性能，并进行故障排查和性能优化。这种集成使得开发人员能够更有效地监控应用程序的健康状况，并及时发现和解决问题‌
 */
public final class ExplicitBucketHistogramAggregation implements Aggregation, AggregatorFactory {

  private static final Aggregation DEFAULT = new ExplicitBucketHistogramAggregation(ExplicitBucketHistogramUtils.DEFAULT_HISTOGRAM_BUCKET_BOUNDARIES);

  public static Aggregation getDefault() {
    return DEFAULT;
  }

  public static Aggregation create(List<Double> bucketBoundaries) {
    return new ExplicitBucketHistogramAggregation(bucketBoundaries);
  }

  // 定义了bucket桶的边界
  private final List<Double> bucketBoundaries;
  private final double[] bucketBoundaryArray;

  private ExplicitBucketHistogramAggregation(List<Double> bucketBoundaries) {
    this.bucketBoundaries = bucketBoundaries;
    // We need to fail here if our bucket boundaries are ill-configured.
    this.bucketBoundaryArray = ExplicitBucketHistogramUtils.createBoundaryArray(bucketBoundaries);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends PointData, U extends ExemplarData> Aggregator<T, U> createAggregator(
      InstrumentDescriptor instrumentDescriptor, ExemplarFilter exemplarFilter) {
    return (Aggregator<T, U>) new DoubleExplicitBucketHistogramAggregator(bucketBoundaryArray,
            () -> ExemplarReservoir.filtered(
                    exemplarFilter,
                    ExemplarReservoir.histogramBucketReservoir(Clock.getDefault(), bucketBoundaries)));
  }

  @Override
  public boolean isCompatibleWithInstrument(InstrumentDescriptor instrumentDescriptor) {
    switch (instrumentDescriptor.getType()) {
      case COUNTER:
      case HISTOGRAM:
        return true;
      default:
        return false;
    }
  }

  @Override
  public String toString() {
    return "ExplicitBucketHistogramAggregation(" + bucketBoundaries.toString() + ")";
  }
}
