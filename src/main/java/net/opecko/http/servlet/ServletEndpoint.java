package net.opecko.http.servlet;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServlet;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Package up a servlet definition.
 */
public class ServletEndpoint {

  private final HttpServlet httpServlet;
  private final ImmutableList<String> paths;

  public ServletEndpoint(final HttpServlet httpServlet, final String... paths) {
    Preconditions.checkArgument(paths.length > 0);
    this.httpServlet = Preconditions.checkNotNull(httpServlet);
    this.paths = ImmutableList.copyOf(Arrays.asList(paths));
  }

  public final HttpServlet getHttpServlet() {
    return httpServlet;
  }

  public final List<String> getPaths() {
    return paths;
  }

  @Override
  public boolean equals(@Nullable final Object o) {
    if (o == this) {
      return true;
    } else if (!(o instanceof ServletEndpoint)) {
      return false;
    }
    ServletEndpoint other = (ServletEndpoint) o;
    return httpServlet == other.httpServlet
        && Objects.equal(paths, other.paths);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(paths);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).omitNullValues()
        .add("servletClass", httpServlet)
        .add("paths", paths)
        .toString();
  }

}
