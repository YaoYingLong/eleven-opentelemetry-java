/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.sdk.metrics.internal.descriptor.Advice;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.state.CallbackRegistration;
import io.opentelemetry.sdk.metrics.internal.state.MeterProviderSharedState;
import io.opentelemetry.sdk.metrics.internal.state.MeterSharedState;
import io.opentelemetry.sdk.metrics.internal.state.SdkObservableMeasurement;
import io.opentelemetry.sdk.metrics.internal.state.WriteableMetricStorage;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Helper to make implementing builders easier. */
abstract class AbstractInstrumentBuilder<BuilderT extends AbstractInstrumentBuilder<?>> {

  static final String DEFAULT_UNIT = "";

  private final MeterProviderSharedState meterProviderSharedState;
  private final InstrumentType type;
  private final InstrumentValueType valueType;
  private String description;
  private String unit;

  protected final MeterSharedState meterSharedState;
  protected final String instrumentName;
  protected final Advice.AdviceBuilder adviceBuilder;

  AbstractInstrumentBuilder(
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      InstrumentType type,
      InstrumentValueType valueType,
      String name,
      String description,
      String unit) {
    this(
        meterProviderSharedState,
        meterSharedState,
        type,
        valueType,
        name,
        description,
        unit,
        Advice.builder());
  }

  AbstractInstrumentBuilder(
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      InstrumentType type,
      InstrumentValueType valueType,
      String name,
      String description,
      String unit,
      Advice.AdviceBuilder adviceBuilder) {
    this.type = type;
    this.valueType = valueType;
    this.instrumentName = name;
    this.description = description;
    this.unit = unit;
    this.meterProviderSharedState = meterProviderSharedState;
    this.meterSharedState = meterSharedState;
    this.adviceBuilder = adviceBuilder;
  }

  protected abstract BuilderT getThis();

  public BuilderT setUnit(String unit) {
    this.unit = unit;
    return getThis();
  }

  public BuilderT setDescription(String description) {
    this.description = description;
    return getThis();
  }

  protected <T> T swapBuilder(SwapBuilder<T> swapper) {
    return swapper.newBuilder(
        meterProviderSharedState,
        meterSharedState,
        instrumentName,
        description,
        unit,
        adviceBuilder);
  }

  final <I extends AbstractInstrument> I buildSynchronousInstrument(BiFunction<InstrumentDescriptor, WriteableMetricStorage, I> instrumentFactory) {
    InstrumentDescriptor descriptor = InstrumentDescriptor.create(instrumentName, description, unit, type, valueType, adviceBuilder.build());
    WriteableMetricStorage storage = meterSharedState.registerSynchronousMetricStorage(descriptor, meterProviderSharedState);
    return instrumentFactory.apply(descriptor, storage);
  }

  final SdkObservableInstrument registerDoubleAsynchronousInstrument(InstrumentType type, Consumer<ObservableDoubleMeasurement> updater) {
    SdkObservableMeasurement sdkObservableMeasurement = buildObservableMeasurement(type);
    Runnable runnable = () -> updater.accept(sdkObservableMeasurement);
    CallbackRegistration callbackRegistration =
        CallbackRegistration.create(Collections.singletonList(sdkObservableMeasurement), runnable);
    meterSharedState.registerCallback(callbackRegistration);
    return new SdkObservableInstrument(meterSharedState, callbackRegistration);
  }

  /**
   * 通过Worker构造方法掉过来传入的InstrumentType为InstrumentType.OBSERVABLE_GAUGE
   * updater为：result -> result.record(queue.size(), Attributes.of(SPAN_PROCESSOR_TYPE_LABEL, SPAN_PROCESSOR_TYPE_VALUE)
   */
  final SdkObservableInstrument registerLongAsynchronousInstrument(InstrumentType type, Consumer<ObservableLongMeasurement> updater) {
    SdkObservableMeasurement sdkObservableMeasurement = buildObservableMeasurement(type);
    // 将从Worker传进来的result -> result.record(queue.size(), Attributes.of(SPAN_PROCESSOR_TYPE_LABEL, SPAN_PROCESSOR_TYPE_VALUE)封装到线程中
    Runnable runnable = () -> updater.accept(sdkObservableMeasurement);
    // 在CallbackRegistration的invokeCallback中会调用Runnable的run方法
    CallbackRegistration callbackRegistration = CallbackRegistration.create(Collections.singletonList(sdkObservableMeasurement), runnable);
    // 将生成的callbackRegistration注册到meterSharedState中
    meterSharedState.registerCallback(callbackRegistration);
    return new SdkObservableInstrument(meterSharedState, callbackRegistration);
  }

  /**
   * 该类是SdkLongGaugeBuilder的父类，instrumentName, description, unit等数据都是之前已经设置过的
   */
  final SdkObservableMeasurement buildObservableMeasurement(InstrumentType type) {
    InstrumentDescriptor descriptor = InstrumentDescriptor.create(instrumentName, description, unit, type, valueType, adviceBuilder.build());
    return meterSharedState.registerObservableMeasurement(descriptor);
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName()
        + "{descriptor="
        + InstrumentDescriptor.create(
            instrumentName, description, unit, type, valueType, adviceBuilder.build())
        + "}";
  }

  @FunctionalInterface
  protected interface SwapBuilder<T> {
    T newBuilder(
        MeterProviderSharedState meterProviderSharedState,
        MeterSharedState meterSharedState,
        String name,
        String description,
        String unit,
        Advice.AdviceBuilder adviceBuilder);
  }
}
