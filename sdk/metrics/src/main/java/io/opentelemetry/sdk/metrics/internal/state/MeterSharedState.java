/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.state;

import static java.util.stream.Collectors.toMap;

import io.opentelemetry.api.internal.GuardedBy;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.export.RegisteredReader;
import io.opentelemetry.sdk.metrics.internal.view.RegisteredView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * State for a {@code Meter}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class MeterSharedState {

  private final Object collectLock = new Object();
  private final Object callbackLock = new Object();

  // 这里保存的是通过AbstractInstrumentBuilder的registerLongAsynchronousInstrument和registerDoubleAsynchronousInstrument方法注册的函数表达式
  @GuardedBy("callbackLock")
  private final List<CallbackRegistration> callbackRegistrations = new ArrayList<>();

  private final Map<RegisteredReader, MetricStorageRegistry> readerStorageRegistries;

  private final InstrumentationScopeInfo instrumentationScopeInfo;

  private MeterSharedState(InstrumentationScopeInfo instrumentationScopeInfo, List<RegisteredReader> registeredReaders) {
    this.instrumentationScopeInfo = instrumentationScopeInfo;
    // 这里其实是遍历从SdkMeter中调用传入的registeredReaders，然后为每个RegisteredReader生成一个MetricStorageRegistry
    this.readerStorageRegistries = registeredReaders.stream().collect(toMap(Function.identity(), unused -> new MetricStorageRegistry()));
  }

  public static MeterSharedState create(InstrumentationScopeInfo instrumentationScopeInfo, List<RegisteredReader> registeredReaders) {
    return new MeterSharedState(instrumentationScopeInfo, registeredReaders);
  }

  /**
   * Unregister the callback.
   *
   * <p>Callbacks are originally registered via {@link #registerCallback(CallbackRegistration)}.
   */
  public void removeCallback(CallbackRegistration callbackRegistration) {
    synchronized (callbackLock) {
      this.callbackRegistrations.remove(callbackRegistration);
    }
  }

  /**
   * Register the callback.
   *
   * <p>The callback will be invoked once per collection until unregistered via {@link
   * #removeCallback(CallbackRegistration)}.
   *
   * 该方法调用的时机是AbstractInstrumentBuilder中的registerDoubleAsynchronousInstrument和registerLongAsynchronousInstrument方法中被调用
   *
   */
  public final void registerCallback(CallbackRegistration callbackRegistration) {
    synchronized (callbackLock) {
      callbackRegistrations.add(callbackRegistration);
    }
  }

  // only visible for testing.
  /** Returns the {@link InstrumentationScopeInfo} for this {@code Meter}. */
  public InstrumentationScopeInfo getInstrumentationScopeInfo() {
    return instrumentationScopeInfo;
  }

  /** Collects all metrics. */
  public List<MetricData> collectAll(RegisteredReader registeredReader, MeterProviderSharedState meterProviderSharedState, long epochNanos) {
    List<CallbackRegistration> currentRegisteredCallbacks;
    synchronized (callbackLock) {
      currentRegisteredCallbacks = new ArrayList<>(callbackRegistrations);
    }
    // Collections across all readers are sequential
    synchronized (collectLock) {
      // 这里通过调用CallbackRegistration的invokeCallback，真正调用通过buildWithCallback方法注册进来的异步方法
      // 也就是调用生成的SdkObservableMeasurement的record方法，这里其实生成points数据，并并存储到对应的MetricStorage中
      for (CallbackRegistration callbackRegistration : currentRegisteredCallbacks) {
        callbackRegistration.invokeCallback(registeredReader, meterProviderSharedState.getStartEpochNanos(), epochNanos);
      }

      /*
       * 这里其实就是将所有MetricStorage中存储的数据获取出来构建成MetricData，并合并返回，包括同步方式和异步方式的,这里是怎么做到对异步的处理的
       * 对于异步方式：
       *    因为在registerObservableMeasurement中同时将生成的AsynchronousMetricStorage
       *    注册到了readerStorageRegistries的MetricStorageRegistry中也同时将AsynchronousMetricStorage保存到了SdkObservableMeasurement
       *    在上面执行SdkObservableMeasurement的record方法时会将数据保存到AsynchronousMetricStorage
       *
       * 对应同步方式：
       *  registerSynchronousMetricStorage中同时将生成的生成的DefaultSynchronousMetricStorage
       *  注册到了readerStorageRegistries的MetricStorageRegistry中也同时将DefaultSynchronousMetricStorage保存到了MultiWritableMetricStorage
       *  最终保存到了具体的如SdkDoubleCounter中，在执行add方法时就将数据生成了
       */
      Collection<MetricStorage> storages = Objects.requireNonNull(readerStorageRegistries.get(registeredReader)).getStorages();
      List<MetricData> result = new ArrayList<>(storages.size());
      for (MetricStorage storage : storages) {
        MetricData current = storage.collect(
                meterProviderSharedState.getResource(),
                getInstrumentationScopeInfo(),
                meterProviderSharedState.getStartEpochNanos(),
                epochNanos);
        // Ignore if the metric data doesn't have any data points, for example when aggregation is
        // Aggregation#drop()
        if (!current.isEmpty()) {
          result.add(current);
        }
      }
      return result;
    }
  }

  /** Reset the meter state, clearing all registered callbacks and storages. */
  public void resetForTest() {
    synchronized (collectLock) {
      synchronized (callbackLock) {
        callbackRegistrations.clear();
      }
      this.readerStorageRegistries.values().forEach(MetricStorageRegistry::resetForTest);
    }
  }

  /** Registers new synchronous storage associated with a given instrument. */
  public final WriteableMetricStorage registerSynchronousMetricStorage(InstrumentDescriptor instrument, MeterProviderSharedState meterProviderSharedState) {
    List<SynchronousMetricStorage> registeredStorages = new ArrayList<>();
    // 这里的readerStorageRegistries是在MeterSharedState构造方法中，通过SdkMeterProvider传入的registeredReaders
    for (Map.Entry<RegisteredReader, MetricStorageRegistry> entry : readerStorageRegistries.entrySet()) {
      RegisteredReader reader = entry.getKey();
      // registry获取到的目前都是一个new MetricStorageRegistry();
      MetricStorageRegistry registry = entry.getValue();
      /*
       * 其实这个地方比较坑，从代码逻辑上看在SdkMeterProvider中调用ViewRegistry构造方法传入的List<RegisteredView>是空列表
       * 在findViews中先遍历registeredViews，如果没有找到，会再调用通过类型从ViewRegistry构造方法中初始化的全类型列表中获取
       */
      for (RegisteredView registeredView : reader.getViewRegistry().findViews(instrument, getInstrumentationScopeInfo())) {
        if (Aggregation.drop() == registeredView.getView().getAggregation()) {
          continue;
        }
        // 这里其实就是想MetricStorageRegistry中注册DefaultSynchronousMetricStorage，其实主要是将具体的Aggregator放入到DefaultSynchronousMetricStorage
        registeredStorages.add(registry.register(SynchronousMetricStorage.create(reader, registeredView, instrument, meterProviderSharedState.getExemplarFilter())));
      }
    }
    if (registeredStorages.size() == 1) {
      return registeredStorages.get(0);
    }
    return new MultiWritableMetricStorage(registeredStorages);
  }

  /** Register new asynchronous storage associated with a given instrument. */
  public final SdkObservableMeasurement registerObservableMeasurement(InstrumentDescriptor instrumentDescriptor) {
    List<AsynchronousMetricStorage<?, ?>> registeredStorages = new ArrayList<>();
    // 这里的readerStorageRegistries是在MeterSharedState构造方法中，通过SdkMeterProvider传入的registeredReaders
    for (Map.Entry<RegisteredReader, MetricStorageRegistry> entry : readerStorageRegistries.entrySet()) {
      RegisteredReader reader = entry.getKey();
      // registry获取到的目前都是一个new MetricStorageRegistry();
      MetricStorageRegistry registry = entry.getValue();
      // 需要确认一下通过在SdkMeterProvider中调用ViewRegistry构造方法传入的List<RegisteredView>是什么时候配置的
      for (RegisteredView registeredView : reader.getViewRegistry().findViews(instrumentDescriptor, getInstrumentationScopeInfo())) {
        if (Aggregation.drop() == registeredView.getView().getAggregation()) {
          continue;
        }
        // 这里其实就是想MetricStorageRegistry中注册DefaultSynchronousMetricStorage，其实主要是将具体的Aggregator放入到DefaultSynchronousMetricStorage
        registeredStorages.add(registry.register(AsynchronousMetricStorage.create(reader, registeredView, instrumentDescriptor)));
      }
    }
    return SdkObservableMeasurement.create(instrumentationScopeInfo, instrumentDescriptor, registeredStorages);
  }
}
