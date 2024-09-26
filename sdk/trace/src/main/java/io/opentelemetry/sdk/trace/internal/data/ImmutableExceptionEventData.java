/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.internal.data;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.internal.AttributeUtil;
import io.opentelemetry.sdk.trace.SpanLimits;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.annotation.concurrent.Immutable;

/** An effectively immutable implementation of {@link ExceptionEventData}. */
@AutoValue
@Immutable
abstract class ImmutableExceptionEventData implements ExceptionEventData {

  private static final AttributeKey<String> EXCEPTION_TYPE =
      AttributeKey.stringKey("exception.type");
  private static final AttributeKey<String> EXCEPTION_MESSAGE =
      AttributeKey.stringKey("exception.message");
  private static final AttributeKey<String> EXCEPTION_STACKTRACE =
      AttributeKey.stringKey("exception.stacktrace");
  private static final String EXCEPTION_EVENT_NAME = "exception";

  /**
   * Returns a new immutable {@code Event}.
   *
   * @param spanLimits limits applied to {@code Event}.
   * @param epochNanos epoch timestamp in nanos of the {@code Event}.
   * @param exception the {@link Throwable exception} of the {@code Event}.
   * @param additionalAttributes the additional {@link Attributes} of the {@code Event}.
   * @return a new immutable {@code Event<T>}
   */
  static ExceptionEventData create(
      SpanLimits spanLimits,
      long epochNanos,
      Throwable exception,
      Attributes additionalAttributes) {

    return new AutoValue_ImmutableExceptionEventData(
        epochNanos, exception, additionalAttributes, spanLimits);
  }

  ImmutableExceptionEventData() {}

  protected abstract SpanLimits getSpanLimits();

  @Override
  public final String getName() {
    return EXCEPTION_EVENT_NAME;
  }

  @Override
  @Memoized
  public Attributes getAttributes() {
    // 获取设置进来的Throwable
    Throwable exception = getException();
    // 默认为Attributes.empty()
    Attributes additionalAttributes = getAdditionalAttributes();
    // 生成ArrayBackedAttributesBuilder
    AttributesBuilder attributesBuilder = Attributes.builder();
    // 获取异常类型并设置
    attributesBuilder.put(EXCEPTION_TYPE, exception.getClass().getCanonicalName());
    String message = exception.getMessage();
    if (message != null) {
      // 获取设置异常信息
      attributesBuilder.put(EXCEPTION_MESSAGE, message);
    }

    StringWriter stringWriter = new StringWriter();
    try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
      exception.printStackTrace(printWriter);
    }
    // 获取异常堆栈信息
    attributesBuilder.put(EXCEPTION_STACKTRACE, stringWriter.toString());
    attributesBuilder.putAll(additionalAttributes);

    SpanLimits spanLimits = getSpanLimits();
    // 构建Attributes通过SpanLimits，如属性值超长的需要做截取
    return AttributeUtil.applyAttributesLimit(attributesBuilder.build(),
        spanLimits.getMaxNumberOfAttributesPerEvent(),spanLimits.getMaxAttributeValueLength());
  }

  @Override
  public final int getTotalAttributeCount() {
    return getAttributes().size();
  }
}
