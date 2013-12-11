package net.opecko.http.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class InvalidRequestServlet extends HttpServlet {

  private static final long serialVersionUID = 20131106L;

  @Override
  protected void service(
      final HttpServletRequest request,
      final HttpServletResponse response
  ) throws ServletException, IOException {
    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    response.setContentType("text/plain");
    response.setContentType("UTF-8");
    response.getWriter().append("404");
  }

}
