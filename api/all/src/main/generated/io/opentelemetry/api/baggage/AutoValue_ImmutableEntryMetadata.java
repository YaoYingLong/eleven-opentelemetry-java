package io.opentelemetry.api.baggage;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_ImmutableEntryMetadata extends ImmutableEntryMetadata {

  private final String value;

  AutoValue_ImmutableEntryMetadata(
      String value) {
    if (value == null) {
      throw new NullPointerException("Null value");
    }
    this.value = value;
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return "ImmutableEntryMetadata{"
        + "value=" + value
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ImmutableEntryMetadata) {
      ImmutableEntryMetadata that = (ImmutableEntryMetadata) o;
      return this.value.equals(that.getValue());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= value.hashCode();
    return h$;
  }

}
