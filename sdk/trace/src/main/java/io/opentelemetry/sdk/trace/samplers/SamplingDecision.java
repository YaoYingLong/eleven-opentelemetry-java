/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.samplers;

/**
 * A decision on whether a span should be recorded, recorded and sampled or dropped.
 * 决定是否应录制、录制和采样或丢弃 span。
 */
public enum SamplingDecision {
  /**
   * Span is dropped. The resulting span will be completely no-op.
   * Span 被丢弃。生成的 span 将是完全无操作的。
   */
  DROP,
  /**
   * Span is recorded only. The resulting span will record all information like timings and
   * attributes but will not be exported. Downstream {@linkplain Sampler#parentBased(Sampler)
   * parent-based} samplers will not sample the span.
   *
   * 仅录制 Span。生成的 span 将记录所有信息，如 timings 和 attributes，但不会导出。
   * 下游SamplerparentBased（Sampler）采样器不会对范围进行采样。
   */
  RECORD_ONLY,
  /**
   * Span is recorded and sampled. The resulting span will record all information like timings and
   * attributes and will be exported.
   *
   * Span 被记录和采样。生成的 span 将记录 timings 和 attributes 等所有信息，并将导出。
   */
  RECORD_AND_SAMPLE,
}
