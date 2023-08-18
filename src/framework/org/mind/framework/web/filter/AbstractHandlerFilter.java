package org.mind.framework.web.filter;

import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2023/8/11
 */
public abstract class AbstractHandlerFilter extends OncePerRequestFilter implements HandlerFilter{

    @Override
    public abstract void doStart(HttpServletRequest request,
                                 HttpServletResponse response,
                                 FilterChain filterChain) throws ServletException, IOException;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                             @NotNull HttpServletResponse response,
                                             @NotNull FilterChain filterChain) throws ServletException, IOException{
        this.doStart(request, response, filterChain);
    }
}
