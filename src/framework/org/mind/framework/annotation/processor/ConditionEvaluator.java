package org.mind.framework.annotation.processor;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.CachedExpressionEvaluator;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.expression.Expression;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 解析<code>@Cachein</code> <code>@Cacheout</code>中的 condition
 *
 * @author: Marcus
 * @date: 2026/10/26
 * @version: 1.0
 */
public class ConditionEvaluator extends CachedExpressionEvaluator {
    public static final String BEAN_NAME = "conditionEvaluator";
    private final Map<ExpressionKey, Expression> exCacheMap = new ConcurrentHashMap<>(64);

    /**
     * @param result 方法返回值
     * @return condition 为空视为 true
     */
    public boolean matches(String condition, MethodInvocation invocation, Class<?> targetClass, Method method, Object result) {
        if (StringUtils.isEmpty(condition))
            return true;

        MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(
                invocation.getThis(), method, invocation.getArguments(), getParameterNameDiscoverer()
        );
        ctx.setVariable("result", result);

        Expression expr = getExpression(exCacheMap, new AnnotatedElementKey(method, targetClass), condition);
        return Boolean.TRUE.equals(expr.getValue(ctx, Boolean.class));
    }
}
