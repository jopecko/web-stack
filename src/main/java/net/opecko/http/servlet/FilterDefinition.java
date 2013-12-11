package net.opecko.http.servlet;

import javax.annotation.Nullable;
import javax.servlet.Filter;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * Package up a filter definition.
 */
public class FilterDefinition {

  private final Filter filter;
  private final String path;

  public FilterDefinition(final Filter filter, final String path) {
    this.filter = Preconditions.checkNotNull(filter);
    this.path = Preconditions.checkNotNull(path);
  }

  public final Filter getFilter() {
    return filter;
  }

  public final String getPath() {
    return path;
  }

  @Override
  public boolean equals(@Nullable final Object o) {
    if (o == this) {
      return true;
    } else if (!(o instanceof FilterDefinition)) {
      return false;
    }
    FilterDefinition other = (FilterDefinition) o;
    return filter == other.filter
        && Objects.equal(path, other.path);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(path);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).omitNullValues()
        .add("filter", filter)
        .add("paths", path)
        .toString();
  }

}
