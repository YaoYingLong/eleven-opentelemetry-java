/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.data;

import io.opentelemetry.api.common.Attributes;
import java.util.List;

/**
 * A point in the metric data model.
 *
 * <p>A point represents the aggregation of measurements recorded with a particular set of {@link
 * Attributes} over some time interval.
 *
 * @since 1.14.0
 */
public interface PointData {
  /**
   * Returns the start time of the aggregation in epoch nanos.
   * 聚合的开始时间
   */
  long getStartEpochNanos();

  /**
   * Returns the end time of the aggregation in epoch nanos.
   * 聚合的结束时间
   */
  long getEpochNanos();

  /**
   * Returns the attributes of the aggregation.
   * 聚合属性
   */
  Attributes getAttributes();

  /**
   * List of exemplars collected from measurements aggregated into this point.
   * 从聚合到此点的测量中收集的示例列表
   */
  List<? extends ExemplarData> getExemplars();
}
