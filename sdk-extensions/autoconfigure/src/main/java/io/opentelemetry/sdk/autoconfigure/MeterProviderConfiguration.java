/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.internal.SdkMeterProviderUtil;
import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarFilter;
import io.opentelemetry.sdk.metrics.internal.state.MetricStorage;
import java.io.Closeable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

final class MeterProviderConfiguration {

  static void configureMeterProvider(SdkMeterProviderBuilder meterProviderBuilder, ConfigProperties config, SpiHelper spiHelper,
      BiFunction<? super MetricExporter, ConfigProperties, ? extends MetricExporter> metricExporterCustomizer, List<Closeable> closeables) {
    // Configure default exemplar filters.
    // 配置默认示例筛选器: 默认为trace_based
    String exemplarFilter = config.getString("otel.metrics.exemplar.filter", "trace_based").toLowerCase(Locale.ROOT);
    switch (exemplarFilter) {
      case "always_off":
        // 通过反射的方式的用SdkMeterProviderBuilder的setExemplarFilter设置AlwaysOffFilter
        SdkMeterProviderUtil.setExemplarFilter(meterProviderBuilder, ExemplarFilter.alwaysOff());
        break;
      case "always_on":
        // 通过反射的方式的用SdkMeterProviderBuilder的setExemplarFilter设置AlwaysOnFilter
        SdkMeterProviderUtil.setExemplarFilter(meterProviderBuilder, ExemplarFilter.alwaysOn());
        break;
      case "trace_based":
      default:
        // 通过反射的方式的用SdkMeterProviderBuilder的setExemplarFilter设置TraceBasedExemplarFilter
        SdkMeterProviderUtil.setExemplarFilter(meterProviderBuilder, ExemplarFilter.traceBased());
        break;
    }
    // 默认值为2000
    int cardinalityLimit = config.getInt("otel.experimental.metrics.cardinality.limit", MetricStorage.DEFAULT_MAX_CARDINALITY);
    if (cardinalityLimit < 1) {
      throw new ConfigurationException("otel.experimental.metrics.cardinality.limit must be >= 1");
    }
    /*
     * 首先通过configureMetricReaders生成，由PeriodicMetricReader包装的OtlpGrpcMetricExporter
     *  - MetricExporter默认是OtlpGrpcMetricExporter，可以通过otel.metrics.exporter配置修改为OtlpJsonLoggingMetricExporter或LoggingMetricExporter，直接打印日志到控制台或日志文件中
     *  - 也可以同时设置多个MetricExporter
     *
     * 然后再通过SdkMeterProviderUtil.registerMetricReaderWithCardinalitySelector方法，通过反射的方式调用SdkMeterProviderBuilder的registerMetricReader
     *  - 将构建的MetricExporter列表和默认值为2000的cardinalityLimit通过函数表达式的方式设置到SdkMeterProviderBuilder的metricReaders（实际是一个HashMap）
     *  - Key：MetricExporter， Value：默认值为2000的cardinalityLimit的函数表达式
     */
    configureMetricReaders(config, spiHelper, metricExporterCustomizer, closeables)
        .forEach(reader -> SdkMeterProviderUtil.registerMetricReaderWithCardinalitySelector(meterProviderBuilder, reader, instrumentType -> cardinalityLimit));
  }

  static List<MetricReader> configureMetricReaders(ConfigProperties config, SpiHelper spiHelper,
      BiFunction<? super MetricExporter, ConfigProperties, ? extends MetricExporter> metricExporterCustomizer, List<Closeable> closeables) {
    Set<String> exporterNames = DefaultConfigProperties.getSet(config, "otel.metrics.exporter");
    if (exporterNames.contains("none")) {
      if (exporterNames.size() > 1) {
        throw new ConfigurationException("otel.metrics.exporter contains none along with other exporters");
      }
      return Collections.emptyList();
    }

    // 默认是OtlpMetricExporterProvider
    if (exporterNames.isEmpty()) {
      exporterNames = Collections.singleton("otlp");
    }
    // 这里构建的其实是PeriodicMetricReader
    return exporterNames.stream()
        .map(exporterName -> MetricExporterConfiguration.configureReader(exporterName, config, spiHelper, metricExporterCustomizer, closeables))
        .collect(Collectors.toList());
  }

  private MeterProviderConfiguration() {}
}
