/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.descriptor;

import com.google.auto.value.AutoValue;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.InstrumentValueType;
import io.opentelemetry.sdk.metrics.internal.debug.SourceInfo;
import java.util.Locale;
import javax.annotation.concurrent.Immutable;

/**
 * InstrumentDescriptor 的主要职责是提供一种方式来定义和标准化监测工具的行为，包括如何创建和命名指标、这些指标的默认属性以及如何处理这些指标的数据
 *
 * Describes an instrument that was registered to record data.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@AutoValue
@Immutable
public abstract class InstrumentDescriptor {

  private final SourceInfo sourceInfo = SourceInfo.fromCurrentStack();
  private int hashcode;

  public static InstrumentDescriptor create(
      String name,
      String description,
      String unit,
      InstrumentType type,
      InstrumentValueType valueType,
      Advice advice) {
    return new AutoValue_InstrumentDescriptor(name, description, unit, type, valueType, advice);
  }

  InstrumentDescriptor() {}

  public abstract String getName();

  public abstract String getDescription();

  public abstract String getUnit();

  /**
   * 目前支持的类型：
   *   COUNTER,
   *   UP_DOWN_COUNTER,
   *   HISTOGRAM,
   *   OBSERVABLE_COUNTER,
   *   OBSERVABLE_UP_DOWN_COUNTER,
   *   OBSERVABLE_GAUGE,
   */
  public abstract InstrumentType getType();

  /**
   * 目前支持的类型：
   *   LONG,
   *   DOUBLE,
   * @return
   */
  public abstract InstrumentValueType getValueType();

  /**
   * Not part of instrument identity. Ignored from {@link #hashCode()} and {@link #equals(Object)}.
   */
  public abstract Advice getAdvice();

  /**
   * Debugging information for this instrument. Ignored from {@link #equals(Object)} and {@link
   * #toString()}.
   */
  public final SourceInfo getSourceInfo() {
    return sourceInfo;
  }

  /**
   * Uses case-insensitive version of {@link #getName()}, ignores {@link #getAdvice()} (not part of
   * instrument identity}, ignores {@link #getSourceInfo()}.
   */
  @Override
  public final int hashCode() {
    int result = hashcode;
    if (result == 0) {
      result = 1;
      result *= 1000003;
      result ^= getName().toLowerCase(Locale.ROOT).hashCode();
      result *= 1000003;
      result ^= getDescription().hashCode();
      result *= 1000003;
      result ^= getUnit().hashCode();
      result *= 1000003;
      result ^= getType().hashCode();
      result *= 1000003;
      result ^= getValueType().hashCode();
      hashcode = result;
    }
    return result;
  }

  /**
   * Uses case-insensitive version of {@link #getName()}, ignores {@link #getAdvice()} (not part of
   * instrument identity}, ignores {@link #getSourceInfo()}.
   */
  @Override
  public final boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof InstrumentDescriptor) {
      InstrumentDescriptor that = (InstrumentDescriptor) o;
      return this.getName().equalsIgnoreCase(that.getName())
          && this.getDescription().equals(that.getDescription())
          && this.getUnit().equals(that.getUnit())
          && this.getType().equals(that.getType())
          && this.getValueType().equals(that.getValueType());
    }
    return false;
  }
}
