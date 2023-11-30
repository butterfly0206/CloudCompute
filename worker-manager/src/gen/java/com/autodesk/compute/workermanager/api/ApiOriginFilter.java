package com.autodesk.compute.workermanager.api;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class ApiOriginFilter implements jakarta.servlet.Filter {

    @Override
    public void doFilter(final jakarta.servlet.ServletRequest servletRequest, final jakarta.servlet.ServletResponse servletResponse, final jakarta.servlet.FilterChain filterChain) throws IOException, jakarta.servlet.ServletException {
        final HttpServletResponse res = (HttpServletResponse) servletResponse;
        res.addHeader("Access-Control-Allow-Origin", "*");
        res.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
        res.addHeader("Access-Control-Allow-Headers", "Content-Type");
        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
    }
}
