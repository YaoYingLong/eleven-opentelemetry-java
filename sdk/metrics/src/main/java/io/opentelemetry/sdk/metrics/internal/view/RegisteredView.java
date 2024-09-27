/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.view;

import com.google.auto.value.AutoValue;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.internal.debug.SourceInfo;
import javax.annotation.concurrent.Immutable;

/**
 * Internal representation of a {@link View} and {@link InstrumentSelector}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 *
 * RegisteredView是OpenTelemetry中的一个接口，它用于表示一个已注册的视图，通常用于指标（metrics）系统中。在OpenTelemetry中，视图是一种定义如何展示和聚合指标数据的方法。
 *
 * RegisteredView接口通常用于定义如何处理和展示一组指标数据，它可能包含如下方法：
 *  1、Aggregation：表示应如何聚合指标数据，例如求和、平均值、最大值等。
 *  2、InstrumentSelector：定义了一组特定的指标工具，这些工具应该应用此视图。
 *  3、AggregationWindow：表示数据应该在多长时间内聚合，例如每5分钟、每1小时等
 */
@AutoValue
@Immutable
public abstract class RegisteredView {

  public static RegisteredView create(InstrumentSelector selector, View view,
      AttributesProcessor viewAttributesProcessor, int cardinalityLimit,
      SourceInfo viewSourceInfo) {
    return new AutoValue_RegisteredView(selector, view, viewAttributesProcessor, cardinalityLimit, viewSourceInfo);
  }

  RegisteredView() {}

  /**
   * Instrument filter for applying this view.
   * 仅将名称设置为*的InstrumentSelector，其他属性都没有设置DefaultAggregation
   */
  public abstract InstrumentSelector getInstrumentSelector();

  /**
   * The view to apply.
   * 默认设置的是将Aggregation属性设置为
   */
  public abstract View getView();

  /**
   * The view's {@link AttributesProcessor}.
   * 默认设置进来的是NoopAttributesProcessor
   */
  public abstract AttributesProcessor getViewAttributesProcessor();

  /**
   * The view's cardinality limit.
   * 默认值为2000
   */
  public abstract int getCardinalityLimit();

  /**
   * The {@link SourceInfo} from where the view was registered.
   * 默认设置的NoSourceInfo.INSTANCE
   */
  public abstract SourceInfo getViewSourceInfo();

  @Override
  public final String toString() {
    return "RegisteredView{"
        + "instrumentSelector="
        + getInstrumentSelector()
        + ", view="
        + getView()
        + "}";
  }
}
