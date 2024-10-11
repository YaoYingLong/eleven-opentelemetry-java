package io.opentelemetry.api.internal;

import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_ImmutableSpanContext extends ImmutableSpanContext {

  private final String traceId;

  private final String spanId;

  private final TraceFlags traceFlags;

  private final TraceState traceState;

  private final boolean remote;

  private final boolean valid;

  AutoValue_ImmutableSpanContext(
      String traceId,
      String spanId,
      TraceFlags traceFlags,
      TraceState traceState,
      boolean remote,
      boolean valid) {
    if (traceId == null) {
      throw new NullPointerException("Null traceId");
    }
    this.traceId = traceId;
    if (spanId == null) {
      throw new NullPointerException("Null spanId");
    }
    this.spanId = spanId;
    if (traceFlags == null) {
      throw new NullPointerException("Null traceFlags");
    }
    this.traceFlags = traceFlags;
    if (traceState == null) {
      throw new NullPointerException("Null traceState");
    }
    this.traceState = traceState;
    this.remote = remote;
    this.valid = valid;
  }

  @Override
  public String getTraceId() {
    return traceId;
  }

  @Override
  public String getSpanId() {
    return spanId;
  }

  @Override
  public TraceFlags getTraceFlags() {
    return traceFlags;
  }

  @Override
  public TraceState getTraceState() {
    return traceState;
  }

  @Override
  public boolean isRemote() {
    return remote;
  }

  @Override
  public boolean isValid() {
    return valid;
  }

  @Override
  public String toString() {
    return "ImmutableSpanContext{"
        + "traceId=" + traceId + ", "
        + "spanId=" + spanId + ", "
        + "traceFlags=" + traceFlags + ", "
        + "traceState=" + traceState + ", "
        + "remote=" + remote + ", "
        + "valid=" + valid
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ImmutableSpanContext) {
      ImmutableSpanContext that = (ImmutableSpanContext) o;
      return this.traceId.equals(that.getTraceId())
          && this.spanId.equals(that.getSpanId())
          && this.traceFlags.equals(that.getTraceFlags())
          && this.traceState.equals(that.getTraceState())
          && this.remote == that.isRemote()
          && this.valid == that.isValid();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= traceId.hashCode();
    h$ *= 1000003;
    h$ ^= spanId.hashCode();
    h$ *= 1000003;
    h$ ^= traceFlags.hashCode();
    h$ *= 1000003;
    h$ ^= traceState.hashCode();
    h$ *= 1000003;
    h$ ^= remote ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= valid ? 1231 : 1237;
    return h$;
  }

}
