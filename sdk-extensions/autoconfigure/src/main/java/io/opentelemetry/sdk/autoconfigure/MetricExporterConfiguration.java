/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import io.opentelemetry.sdk.autoconfigure.internal.NamedSpiManager;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ConfigurableMetricReaderProvider;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.ConfigurableMetricExporterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import java.io.Closeable;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

final class MetricExporterConfiguration {

  private static final Duration DEFAULT_EXPORT_INTERVAL = Duration.ofMinutes(1);
  private static final Map<String, String> EXPORTER_ARTIFACT_ID_BY_NAME;
  private static final Map<String, String> READER_ARTIFACT_ID_BY_NAME;

  static {
    EXPORTER_ARTIFACT_ID_BY_NAME = new HashMap<>();
    EXPORTER_ARTIFACT_ID_BY_NAME.put("logging", "opentelemetry-exporter-logging");
    EXPORTER_ARTIFACT_ID_BY_NAME.put("logging-otlp", "opentelemetry-exporter-logging-otlp");
    EXPORTER_ARTIFACT_ID_BY_NAME.put("otlp", "opentelemetry-exporter-otlp");

    READER_ARTIFACT_ID_BY_NAME = new HashMap<>();
    READER_ARTIFACT_ID_BY_NAME.put("prometheus", "opentelemetry-exporter-prometheus");
  }

  static MetricReader configureReader(String name, ConfigProperties config, SpiHelper spiHelper,
      BiFunction<? super MetricExporter, ConfigProperties, ? extends MetricExporter> metricExporterCustomizer, List<Closeable> closeables) {
    /**
     * 通过SPI机制加载系统已有的ConfigurableMetricExporterProvider: 目前支持的
     *    EXPORTER_ARTIFACT_ID_BY_NAME.put("logging", "opentelemetry-exporter-logging");  LoggingMetricExporterProvider
     *    EXPORTER_ARTIFACT_ID_BY_NAME.put("logging-otlp", "opentelemetry-exporter-logging-otlp"); LoggingMetricExporterProvider
     *    EXPORTER_ARTIFACT_ID_BY_NAME.put("otlp", "opentelemetry-exporter-otlp");  OtlpMetricExporterProvider
     *
     * 可以通过-Dotel.metrics.exporter=logging-otlp参数来设置，默认是通过OtlpMetricExporterProvider构建的OtlpGrpcMetricExporter
     */
    NamedSpiManager<MetricExporter> spiExportersManager = metricExporterSpiManager(config, spiHelper);
    // 通过名称获取加载的MetricExporter，默认是通过OtlpMetricExporterProvider构建的OtlpGrpcMetricExporter
    MetricExporter metricExporter = configureExporter(name, spiExportersManager);
    // If no metric exporter with name, try to load metric reader
    // 如果没有MetricExporter，则尝试加载MetricReader
    if (metricExporter == null) {
      // 通过SPI机制加载ConfigurableMetricReaderProvider，目前仅支持PrometheusMetricReaderProvider
      NamedSpiManager<MetricReader> spiMetricReadersManager = metricReadersSpiManager(config, spiHelper);
      // 这里的MetricReader默认是PrometheusMetricReaderProvider
      MetricReader metricReader = configureMetricReader(name, spiMetricReadersManager);
      if (metricReader != null) {
        closeables.add(metricReader);
        return metricReader;
      }
      // No exporter or reader with the name
      throw new ConfigurationException("Unrecognized value for otel.metrics.exporter: " + name);
    }

    // Customize metric exporter and associate it with PeriodicMetricReader
    closeables.add(metricExporter);  // metricExporter默认是OtlpGrpcMetricExporter
    MetricExporter customizedMetricExporter = metricExporterCustomizer.apply(metricExporter, config);
    if (customizedMetricExporter != metricExporter) {
      closeables.add(customizedMetricExporter);
    }

    // 这里其实是在PeriodicMetricReader中构建一个ScheduledThreadPoolExecutor，用于周期执行export任务
    // 最终应该会执行该MetricReader的start方法，以开启ScheduledThreadPoolExecutor周期执行逻辑
    MetricReader reader = PeriodicMetricReader.builder(customizedMetricExporter)
            // 设置export周期，默认是1分钟
            .setInterval(config.getDuration("otel.metric.export.interval", DEFAULT_EXPORT_INTERVAL))
            .build();
    closeables.add(reader);
    return reader;
  }

  // Visible for testing
  static NamedSpiManager<MetricReader> metricReadersSpiManager(
      ConfigProperties config, SpiHelper spiHelper) {
    return spiHelper.loadConfigurable(
        ConfigurableMetricReaderProvider.class,
        ConfigurableMetricReaderProvider::getName,
        ConfigurableMetricReaderProvider::createMetricReader,
        config);
  }

  // Visible for testing.
  @Nullable
  static MetricReader configureMetricReader(
      String name, NamedSpiManager<MetricReader> spiMetricReadersManager) {
    MetricReader metricReader = spiMetricReadersManager.getByName(name);
    if (metricReader == null) {
      String artifactId = READER_ARTIFACT_ID_BY_NAME.get(name);
      if (artifactId != null) {
        throw missingArtifactException(name, artifactId);
      }
      return null;
    }
    return metricReader;
  }

  // Visible for testing
  static NamedSpiManager<MetricExporter> metricExporterSpiManager(
      ConfigProperties config, SpiHelper spiHelper) {
    return spiHelper.loadConfigurable(
        ConfigurableMetricExporterProvider.class,
        ConfigurableMetricExporterProvider::getName,
        ConfigurableMetricExporterProvider::createExporter,
        config);
  }

  // Visible for testing.
  @Nullable
  static MetricExporter configureExporter(String name, NamedSpiManager<MetricExporter> spiExportersManager) {
    MetricExporter metricExporter = spiExportersManager.getByName(name);
    if (metricExporter == null) {
      String artifactId = EXPORTER_ARTIFACT_ID_BY_NAME.get(name);
      if (artifactId != null) {
        throw missingArtifactException(name, artifactId);
      }
      return null;
    }
    return metricExporter;
  }

  private static ConfigurationException missingArtifactException(
      String exporterName, String artifactId) {
    return new ConfigurationException(
        "otel.metrics.exporter set to \""
            + exporterName
            + "\" but "
            + artifactId
            + " not found on classpath. Make sure to add it as a dependency.");
  }

  private MetricExporterConfiguration() {}
}
