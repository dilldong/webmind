package org.mind.framework.web.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface HandlerFilter extends Filter {
    void doStart(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException;
}
