/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.exemplar;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData;
import io.opentelemetry.sdk.metrics.data.ExemplarData;
import io.opentelemetry.sdk.metrics.data.LongExemplarData;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * An interface for an exemplar reservoir of samples.
 *
 * <p>This represents a reservoir for a specific "point" of metric data.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change at any time.
 *
 * ExemplarReservoir的主要作用是：
 *    1、提供一种机制，用于在度量值上采样，从而可以提供关于这些值的例子或者参考点。
 *    2、可以用于提供对于某个时间序列的度量值的更详细的信息，例如，这个时间序列的最大值、最小值或者平均值等。
 *
 * ExemplarReservoir可能会有不同的实现方式，但是它们都需要满足以下的要求：
 *    1、能够从度量值的流中有效地抽样。
 *    2、抽样的结果需要是无偏的，也就是说，抽样结果应该能代表整个度量值的分布。
 *    3、应该有一定的抽样概率，以便能够代表整个度量值的分布。
 */
public interface ExemplarReservoir<T extends ExemplarData> {

  /** Wraps a {@link ExemplarReservoir} with a measurement pre-filter. */
  static <T extends ExemplarData> ExemplarReservoir<T> filtered(
      ExemplarFilter filter, ExemplarReservoir<T> original) {
    return new FilteredExemplarReservoir<>(filter, original);
  }

  /** A double exemplar reservoir that stores no exemplars. */
  static ExemplarReservoir<DoubleExemplarData> doubleNoSamples() {
    return NoopExemplarReservoir.DOUBLE_INSTANCE;
  }

  /** A long exemplar reservoir that stores no exemplars. */
  static ExemplarReservoir<LongExemplarData> longNoSamples() {
    return NoopExemplarReservoir.LONG_INSTANCE;
  }

  /**
   * A double reservoir with fixed size that stores the given number of exemplars.
   *
   * @param clock The clock to use when annotating measurements with time.
   * @param size The maximum number of exemplars to preserve.
   * @param randomSupplier The random number generator to use for sampling.
   *
   * 是一个固定大小的例子集合，用于存储数据点。当数据点超过这个大小时，会使用某种策略来替换旧的数据点，例如，最老的数据点可能会被最新的数据点替换。
   */
  static ExemplarReservoir<DoubleExemplarData> doubleFixedSizeReservoir(
      Clock clock, int size, Supplier<Random> randomSupplier) {
    return RandomFixedSizeExemplarReservoir.createDouble(clock, size, randomSupplier);
  }

  /**
   * ·
   *
   * @param clock The clock to use when annotating measurements with time.
   * @param size The maximum number of exemplars to preserve.
   * @param randomSupplier The random number generator to use for sampling.
   */
  static ExemplarReservoir<LongExemplarData> longFixedSizeReservoir(
      Clock clock, int size, Supplier<Random> randomSupplier) {
    return RandomFixedSizeExemplarReservoir.createLong(clock, size, randomSupplier);
  }

  /**
   * A Reservoir sampler that preserves the latest seen measurement per-histogram bucket.
   *
   * @param clock The clock to use when annotating measurements with time.
   * @param boundaries A list of (inclusive) upper bounds for the histogram. Should be in order from
   *     lowest to highest.
   */
  static ExemplarReservoir<DoubleExemplarData> histogramBucketReservoir(
      Clock clock, List<Double> boundaries) {
    return new HistogramExemplarReservoir(clock, boundaries);
  }

  /** Offers a {@code double} measurement to be sampled. */
  void offerDoubleMeasurement(double value, Attributes attributes, Context context);

  /** Offers a {@code long} measurement to be sampled. */
  void offerLongMeasurement(long value, Attributes attributes, Context context);

  /**
   * Returns an immutable list of Exemplars for exporting from the current reservoir.
   *
   * <p>Additionally, clears the reservoir for the next sampling period.
   *
   * @param pointAttributes the {@link Attributes} associated with the metric point. {@link
   *     ExemplarData}s should filter these out of their final data state.
   * @return An (immutable) list of sampled exemplars for this point. Implementers are expected to
   *     filter out {@code pointAttributes} from the original recorded attributes.
   */
  List<T> collectAndReset(Attributes pointAttributes);
}
