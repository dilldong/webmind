package org.mind.framework.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * @version 1.0
 * @author Marcus
 * @date 2023/8/11
 */
public abstract class AbstractHandlerFilter extends OncePerRequestFilter implements HandlerFilter {

    @Override
    public abstract void doStart(HttpServletRequest request,
                                 HttpServletResponse response,
                                 FilterChain filterChain) throws ServletException, IOException;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        this.doStart(request, response, filterChain);
    }
}
