package io.opentelemetry.api.trace;

import java.util.List;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_ArrayBasedTraceState extends ArrayBasedTraceState {

  private final List<String> entries;

  AutoValue_ArrayBasedTraceState(
      List<String> entries) {
    if (entries == null) {
      throw new NullPointerException("Null entries");
    }
    this.entries = entries;
  }

  @Override
  List<String> getEntries() {
    return entries;
  }

  @Override
  public String toString() {
    return "ArrayBasedTraceState{"
        + "entries=" + entries
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ArrayBasedTraceState) {
      ArrayBasedTraceState that = (ArrayBasedTraceState) o;
      return this.entries.equals(that.getEntries());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= entries.hashCode();
    return h$;
  }

}
