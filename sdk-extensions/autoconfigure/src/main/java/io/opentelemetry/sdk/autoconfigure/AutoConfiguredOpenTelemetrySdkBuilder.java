/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.events.GlobalEventEmitterProvider;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.internal.SdkEventEmitterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * A builder for configuring auto-configuration of the OpenTelemetry SDK. Notably, auto-configured
 * components can be customized, for example by delegating to them from a wrapper that tweaks
 * behavior such as filtering out telemetry attributes.
 *
 * @since 1.28.0
 */
public final class AutoConfiguredOpenTelemetrySdkBuilder implements AutoConfigurationCustomizer {

  private static final Logger logger =
      Logger.getLogger(AutoConfiguredOpenTelemetrySdkBuilder.class.getName());

  @Nullable private ConfigProperties config;

  private BiFunction<SdkTracerProviderBuilder, ConfigProperties, SdkTracerProviderBuilder>
      tracerProviderCustomizer = (a, unused) -> a;
  private BiFunction<? super TextMapPropagator, ConfigProperties, ? extends TextMapPropagator>
      propagatorCustomizer = (a, unused) -> a;
  private BiFunction<? super SpanExporter, ConfigProperties, ? extends SpanExporter>
      spanExporterCustomizer = (a, unused) -> a;
  private BiFunction<? super Sampler, ConfigProperties, ? extends Sampler> samplerCustomizer =
      (a, unused) -> a;

  private BiFunction<SdkMeterProviderBuilder, ConfigProperties, SdkMeterProviderBuilder>
      meterProviderCustomizer = (a, unused) -> a;
  private BiFunction<? super MetricExporter, ConfigProperties, ? extends MetricExporter>
      metricExporterCustomizer = (a, unused) -> a;

  private BiFunction<SdkLoggerProviderBuilder, ConfigProperties, SdkLoggerProviderBuilder>
      loggerProviderCustomizer = (a, unused) -> a;
  private BiFunction<? super LogRecordExporter, ConfigProperties, ? extends LogRecordExporter>
      logRecordExporterCustomizer = (a, unused) -> a;

  private BiFunction<? super Resource, ConfigProperties, ? extends Resource> resourceCustomizer =
      (a, unused) -> a;

  private Supplier<Map<String, String>> propertiesSupplier = Collections::emptyMap;

  private final List<Function<ConfigProperties, Map<String, String>>> propertiesCustomizers =
      new ArrayList<>();

  private SpiHelper spiHelper = SpiHelper.create(AutoConfiguredOpenTelemetrySdk.class.getClassLoader());

  private boolean registerShutdownHook = true;

  private boolean setResultAsGlobal = false;

  private boolean customized;

  AutoConfiguredOpenTelemetrySdkBuilder() {}

  /**
   * Sets the {@link ConfigProperties} to use when resolving properties for auto-configuration.
   * {@link #addPropertiesSupplier(Supplier)} and {@link #addPropertiesCustomizer(Function)} will
   * have no effect if this method is used.
   */
  AutoConfiguredOpenTelemetrySdkBuilder setConfig(ConfigProperties config) {
    requireNonNull(config, "config");
    this.config = config;
    return this;
  }

  /**
   * Adds a {@link BiFunction} to invoke the with the {@link SdkTracerProviderBuilder} to allow
   * customization. The return value of the {@link BiFunction} will replace the passed-in argument.
   *
   * <p>Multiple calls will execute the customizers in order.
   */
  @Override
  public AutoConfiguredOpenTelemetrySdkBuilder addTracerProviderCustomizer(
      BiFunction<SdkTracerProviderBuilder, ConfigProperties, SdkTracerProviderBuilder> tracerProviderCustomizer) {
    requireNonNull(tracerProviderCustomizer, "tracerProviderCustomizer");
    this.tracerProviderCustomizer = mergeCustomizer(this.tracerProviderCustomizer, tracerProviderCustomizer);
    return this;
  }

  /**
   * Adds a {@link BiFunction} to invoke with the default autoconfigured {@link TextMapPropagator}
   * to allow customization. The return value of the {@link BiFunction} will replace the passed-in
   * argument.
   *
   * <p>Multiple calls will execute the customizers in order.
   */
  @Override
  public AutoConfiguredOpenTelemetrySdkBuilder addPropagatorCustomizer(
      BiFunction<? super TextMapPropagator, ConfigProperties, ? extends TextMapPropagator>
          propagatorCustomizer) {
    requireNonNull(propagatorCustomizer, "propagatorCustomizer");
    this.propagatorCustomizer = mergeCustomizer(this.propagatorCustomizer, propagatorCustomizer);
    return this;
  }

  /**
   * Adds a {@link BiFunction} to invoke with the default autoconfigured {@link Resource} to allow
   * customization. The return value of the {@link BiFunction} will replace the passed-in argument.
   *
   * <p>Multiple calls will execute the customizers in order.
   */
  @Override
  public AutoConfiguredOpenTelemetrySdkBuilder addResourceCustomizer(
      BiFunction<? super Resource, ConfigProperties, ? extends Resource> resourceCustomizer) {
    requireNonNull(resourceCustomizer, "resourceCustomizer");
    this.resourceCustomizer = mergeCustomizer(this.resourceCustomizer, resourceCustomizer);
    return this;
  }

  /**
   * Adds a {@link BiFunction} to invoke with the default autoconfigured {@link Sampler} to allow
   * customization. The return value of the {@link BiFunction} will replace the passed-in argument.
   *
   * <p>Multiple calls will execute the customizers in order.
   */
  @Override
  public AutoConfiguredOpenTelemetrySdkBuilder addSamplerCustomizer(
      BiFunction<? super Sampler, ConfigProperties, ? extends Sampler> samplerCustomizer) {
    requireNonNull(samplerCustomizer, "samplerCustomizer");
    this.samplerCustomizer = mergeCustomizer(this.samplerCustomizer, samplerCustomizer);
    return this;
  }

  /**
   * Adds a {@link BiFunction} to invoke with the default autoconfigured {@link SpanExporter} to
   * allow customization. The return value of the {@link BiFunction} will replace the passed-in
   * argument.
   *
   * <p>Multiple calls will execute the customizers in order.
   */
  @Override
  public AutoConfiguredOpenTelemetrySdkBuilder addSpanExporterCustomizer(
      BiFunction<? super SpanExporter, ConfigProperties, ? extends SpanExporter>
          spanExporterCustomizer) {
    requireNonNull(spanExporterCustomizer, "spanExporterCustomizer");
    this.spanExporterCustomizer =
        mergeCustomizer(this.spanExporterCustomizer, spanExporterCustomizer);
    return this;
  }

  /**
   * Adds a {@link Supplier} of a map of property names and values to use as defaults for the {@link
   * ConfigProperties} used during auto-configuration. The order of precedence of properties is
   * system properties > environment variables > the suppliers registered with this method.
   *
   * <p>Multiple calls will cause properties to be merged in order, with later ones overwriting
   * duplicate keys in earlier ones.
   */
  @Override
  public AutoConfiguredOpenTelemetrySdkBuilder addPropertiesSupplier(
      Supplier<Map<String, String>> propertiesSupplier) {
    requireNonNull(propertiesSupplier, "propertiesSupplier");
    this.propertiesSupplier = mergeProperties(this.propertiesSupplier, propertiesSupplier);
    return this;
  }

  /**
   * Adds a {@link Function} to invoke the with the {@link ConfigProperties} to allow customization.
   * The return value of the {@link Function} will be merged into the {@link ConfigProperties}
   * before it is used for auto-configuration, overwriting the properties that are already there.
   *
   * <p>Multiple calls will cause properties to be merged in order, with later ones overwriting
   * duplicate keys in earlier ones.
   */
  @Override
  public AutoConfiguredOpenTelemetrySdkBuilder addPropertiesCustomizer(
      Function<ConfigProperties, Map<String, String>> propertiesCustomizer) {
    requireNonNull(propertiesCustomizer, "propertiesCustomizer");
    this.propertiesCustomizers.add(propertiesCustomizer);
    return this;
  }

  /**
   * Adds a {@link BiFunction} to invoke the with the {@link SdkMeterProviderBuilder} to allow
   * customization. The return value of the {@link BiFunction} will replace the passed-in argument.
   *
   * <p>Multiple calls will execute the customizers in order.
   */
  @Override
  public AutoConfiguredOpenTelemetrySdkBuilder addMeterProviderCustomizer(
      BiFunction<SdkMeterProviderBuilder, ConfigProperties, SdkMeterProviderBuilder> meterProviderCustomizer) {
    requireNonNull(meterProviderCustomizer, "meterProviderCustomizer");
    this.meterProviderCustomizer = mergeCustomizer(this.meterProviderCustomizer, meterProviderCustomizer);
    return this;
  }

  /**
   * Adds a {@link BiFunction} to invoke with the default autoconfigured {@link SpanExporter} to
   * allow customization. The return value of the {@link BiFunction} will replace the passed-in
   * argument.
   *
   * <p>Multiple calls will execute the customizers in order.
   */
  @Override
  public AutoConfiguredOpenTelemetrySdkBuilder addMetricExporterCustomizer(
      BiFunction<? super MetricExporter, ConfigProperties, ? extends MetricExporter>
          metricExporterCustomizer) {
    requireNonNull(metricExporterCustomizer, "metricExporterCustomizer");
    this.metricExporterCustomizer =
        mergeCustomizer(this.metricExporterCustomizer, metricExporterCustomizer);
    return this;
  }

  /**
   * Adds a {@link BiFunction} to invoke the with the {@link SdkLoggerProviderBuilder} to allow
   * customization. The return value of the {@link BiFunction} will replace the passed-in argument.
   *
   * <p>Multiple calls will execute the customizers in order.
   */
  @Override
  public AutoConfiguredOpenTelemetrySdkBuilder addLoggerProviderCustomizer(
      BiFunction<SdkLoggerProviderBuilder, ConfigProperties, SdkLoggerProviderBuilder>
          loggerProviderCustomizer) {
    requireNonNull(loggerProviderCustomizer, "loggerProviderCustomizer");
    this.loggerProviderCustomizer =
        mergeCustomizer(this.loggerProviderCustomizer, loggerProviderCustomizer);
    return this;
  }

  /**
   * Adds a {@link BiFunction} to invoke with the default autoconfigured {@link LogRecordExporter}
   * to allow customization. The return value of the {@link BiFunction} will replace the passed-in
   * argument.
   *
   * <p>Multiple calls will execute the customizers in order.
   */
  @Override
  public AutoConfiguredOpenTelemetrySdkBuilder addLogRecordExporterCustomizer(
      BiFunction<? super LogRecordExporter, ConfigProperties, ? extends LogRecordExporter>
          logRecordExporterCustomizer) {
    requireNonNull(logRecordExporterCustomizer, "logRecordExporterCustomizer");
    this.logRecordExporterCustomizer =
        mergeCustomizer(this.logRecordExporterCustomizer, logRecordExporterCustomizer);
    return this;
  }

  /**
   * Disable the registration of a shutdown hook to shut down the SDK when appropriate. By default,
   * the shutdown hook is registered.
   *
   * <p>Skipping the registration of the shutdown hook may cause unexpected behavior. This
   * configuration is for SDK consumers that require control over the SDK lifecycle. In this case,
   * alternatives must be provided by the SDK consumer to shut down the SDK.
   */
  public AutoConfiguredOpenTelemetrySdkBuilder disableShutdownHook() {
    this.registerShutdownHook = false;
    return this;
  }

  /**
   * Sets whether the configured {@link OpenTelemetrySdk} should be set as the application's
   * {@linkplain io.opentelemetry.api.GlobalOpenTelemetry global} instance.
   *
   * <p>By default, {@link GlobalOpenTelemetry} is not set.
   */
  public AutoConfiguredOpenTelemetrySdkBuilder setResultAsGlobal() {
    this.setResultAsGlobal = true;
    return this;
  }

  /** Sets the {@link ClassLoader} to be used to load SPI implementations. */
  public AutoConfiguredOpenTelemetrySdkBuilder setServiceClassLoader(
      ClassLoader serviceClassLoader) {
    requireNonNull(serviceClassLoader, "serviceClassLoader");
    this.spiHelper = SpiHelper.create(serviceClassLoader);
    return this;
  }

  /**
   * Returns a new {@link AutoConfiguredOpenTelemetrySdk} holding components auto-configured using
   * the settings of this {@link AutoConfiguredOpenTelemetrySdkBuilder}.
   */
  public AutoConfiguredOpenTelemetrySdk build() {
    if (!customized) {
      customized = true;
      /*
       * 这里是通过SPI机制加载SdkTracerProviderConfigurer接口子类，并调用其configure方法，该方法可以去修改设置SdkTracerProviderBuilder中的属性
       * 这里调用SdkTracerProviderConfigurer#configure方法是会生成一个函数表达式列表，存储到当前类的tracerProviderCustomizer
       */
      mergeSdkTracerProviderConfigurer();
      /*
       * 这里也是通过SPI机制加载AutoConfigurationCustomizerProvider接口子类，并调用其customize方法
       * 通过这种方式可以向当前类添加：tracerProviderCustomizer、propagatorCustomizer、meterProviderCustomizer、metricExporterCustomizer等各种属性
       * 可以通过添加propertiesCustomizers这种方式添加或修改OpenTelemetry配置
       *
       * 这里添加的各种函数表达式，会影响后面各个组件的创建，因为各个组件的创建都会调用到这里添加的函数表达式
       */
      for (AutoConfigurationCustomizerProvider customizer : spiHelper.loadOrdered(AutoConfigurationCustomizerProvider.class)) {
        customizer.customize(this);
      }
    }

    // 这里就会调用上面通过自定义AutoConfigurationCustomizerProvider添加的propertiesCustomizers的函数表达式，真正去覆盖并merge默认配置和自定义配置
    ConfigProperties config = getConfig();

    /*
     * 这首先会通过ConfigProperties中的otel.java.enabled.resource.providers和otel.java.disabled.resource.providers中配置的ResourceProvider
     * 来添加或排除ResourceProvider，即包含在enabled中的，且不包含在disabled中的ResourceProvider，执行其createResource方法创建Resource并Merge
     * 到Default的Resource中，然后再调用通过自定义AutoConfigurationCustomizerProvider添加的resourceCustomizer的函数表达式列表
     */
    Resource resource = ResourceConfiguration.configureResource(config, spiHelper, resourceCustomizer);

    // Track any closeable resources created throughout configuration. If an exception short
    // circuits configuration, partially configured components will be closed.
    List<Closeable> closeables = new ArrayList<>();

    try {
      /*
       * 构建OpenTelemetrySdk，并初始化SdkTracerProvider、SdkMeterProvider、SdkLoggerProvider，这里其实都是一个空壳
       */
      OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().build();
      boolean sdkEnabled = !config.getBoolean("otel.sdk.disabled", false);

      if (sdkEnabled) {
        // 这里其实就是new一个SdkMeterProviderBuilder
        SdkMeterProviderBuilder meterProviderBuilder = SdkMeterProvider.builder();
        meterProviderBuilder.setResource(resource); // 设置resource到meterProviderBuilder中
        /*
         * 这里就是干了两件事
         * 1、通过反射的方式嗲用SdkMeterProviderBuilder的setExemplarFilter设置TraceBasedExemplarFilter
         * 2、将构建的MetricExporter列表和默认值为2000的cardinalityLimit通过函数表达式的方式设置到SdkMeterProviderBuilder的metricReaders（实际是一个HashMap）
         */
        MeterProviderConfiguration.configureMeterProvider(meterProviderBuilder, config, spiHelper, metricExporterCustomizer, closeables);
        // 调用通过自定义AutoConfigurationCustomizerProvider添加的meterProviderCustomizer的函数表达式列表，作用是修改SdkMeterProviderBuilder
        meterProviderBuilder = meterProviderCustomizer.apply(meterProviderBuilder, config);
        /*
         * 比较重要也比较复杂
         *  1、向PeriodicMetricReader中注册持有的CollectionRegistration为SdkCollectionRegistration
         */
        SdkMeterProvider meterProvider = meterProviderBuilder.build();
        closeables.add(meterProvider);

        // 这里其实就是new一个SdkTracerProviderBuilder
        SdkTracerProviderBuilder tracerProviderBuilder = SdkTracerProvider.builder();
        tracerProviderBuilder.setResource(resource);  // 设置resource到tracerProviderBuilder中
        // 创建SpanLimits、Sampler采样器、SpanExporter列表、SpanProcessor列表等并添加到tracerProviderBuilder中用于构建TracerProvider对象
        TracerProviderConfiguration.configureTracerProvider(tracerProviderBuilder,
            config, spiHelper, meterProvider, spanExporterCustomizer, samplerCustomizer, closeables);
        // 调用通过自定义AutoConfigurationCustomizerProvider添加的tracerProviderCustomizer的函数表达式列表，作用是修改SdkTracerProviderBuilder
        tracerProviderBuilder = tracerProviderCustomizer.apply(tracerProviderBuilder, config);
        SdkTracerProvider tracerProvider = tracerProviderBuilder.build();
        closeables.add(tracerProvider);

        SdkLoggerProviderBuilder loggerProviderBuilder = SdkLoggerProvider.builder();
        loggerProviderBuilder.setResource(resource);
        LoggerProviderConfiguration.configureLoggerProvider(loggerProviderBuilder,
            config, spiHelper, meterProvider, logRecordExporterCustomizer, closeables);
        loggerProviderBuilder = loggerProviderCustomizer.apply(loggerProviderBuilder, config);
        SdkLoggerProvider loggerProvider = loggerProviderBuilder.build();
        closeables.add(loggerProvider);

        /*
         * 通过SPI机制加载ConfigurablePropagatorProvider，并执行getPropagator方法获取具体的TextMapPropagator列表
         * 从ConfigProperties中获取otel.propagators配置配置的TextMapPropagator名称列表，默认为tracecontext, baggage
         * 即默认配置为W3CTraceContextPropagator和W3CBaggagePropagator
         *
         * 然后再对每个配置的TextMapPropagator调用通过自定义AutoConfigurationCustomizerProvider添加的propagatorCustomizer的函数表达式列表
         */
        ContextPropagators propagators = PropagatorConfiguration.configurePropagators(config, spiHelper, propagatorCustomizer);
        // 将生成的SdkTracerProvider、SdkLoggerProvider、SdkMeterProvider、ContextPropagators设置到OpenTelemetrySdk中
        OpenTelemetrySdkBuilder sdkBuilder = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setLoggerProvider(loggerProvider)
                .setMeterProvider(meterProvider)
                .setPropagators(propagators);

        openTelemetrySdk = sdkBuilder.build();
      }

      // NOTE: Shutdown hook registration is untested. Modify with caution.
      if (registerShutdownHook) {
        Runtime.getRuntime().addShutdownHook(shutdownHook(openTelemetrySdk));
      }

      if (setResultAsGlobal) {
        GlobalOpenTelemetry.set(openTelemetrySdk);
        GlobalEventEmitterProvider.set(SdkEventEmitterProvider.create(openTelemetrySdk.getSdkLoggerProvider()));
        logger.log(Level.FINE, "Global OpenTelemetry set to {0} by autoconfiguration", openTelemetrySdk);
      }
      return AutoConfiguredOpenTelemetrySdk.create(openTelemetrySdk, resource, config);
    } catch (RuntimeException e) {
      logger.info("Error encountered during autoconfiguration. Closing partially configured components.");
      for (Closeable closeable : closeables) {
        try {
          logger.fine("Closing " + closeable.getClass().getName());
          closeable.close();
        } catch (IOException ex) {
          logger.warning("Error closing " + closeable.getClass().getName() + ": " + ex.getMessage());
        }
      }
      if (e instanceof ConfigurationException) {
        throw e;
      }
      throw new ConfigurationException("Unexpected configuration error", e);
    }
  }

  @SuppressWarnings("deprecation") // Support deprecated SdkTracerProviderConfigurer
  private void mergeSdkTracerProviderConfigurer() {
    for (io.opentelemetry.sdk.autoconfigure.spi.traces.SdkTracerProviderConfigurer configurer :
        spiHelper.load(io.opentelemetry.sdk.autoconfigure.spi.traces.SdkTracerProviderConfigurer.class)) {
      addTracerProviderCustomizer(
          (builder, config) -> {
            configurer.configure(builder, config);
            return builder;
          });
    }
  }

  private ConfigProperties getConfig() {
    ConfigProperties config = this.config;
    if (config == null) {
      config = computeConfigProperties();
    }
    return config;
  }

  private ConfigProperties computeConfigProperties() {
    DefaultConfigProperties properties = DefaultConfigProperties.create(propertiesSupplier.get());
    for (Function<ConfigProperties, Map<String, String>> customizer : propertiesCustomizers) {
      Map<String, String> overrides = customizer.apply(properties);
      properties = properties.withOverrides(overrides);
    }
    return properties;
  }

  // Visible for testing
  Thread shutdownHook(OpenTelemetrySdk sdk) {
    return new Thread(sdk::close);
  }

  private static <I, O1, O2> BiFunction<I, ConfigProperties, O2> mergeCustomizer(
      BiFunction<? super I, ConfigProperties, ? extends O1> first,
      BiFunction<? super O1, ConfigProperties, ? extends O2> second) {
    return (I configured, ConfigProperties config) -> {
      O1 firstResult = first.apply(configured, config);
      return second.apply(firstResult, config);
    };
  }

  private static Supplier<Map<String, String>> mergeProperties(
      Supplier<Map<String, String>> first, Supplier<Map<String, String>> second) {
    return () -> {
      Map<String, String> merged = new HashMap<>();
      merged.putAll(first.get());
      merged.putAll(second.get());
      return merged;
    };
  }
}
