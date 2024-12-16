package org.mind.framework.exception;

/**
 * 拦截检查每个到达受保护的资源,接受用户名和密码，通过用户的资格系统验证用户
 *
 * @author dp
 */
public class AuthenticatorException extends BaseException {

    private static final long serialVersionUID = -4294788851970815693L;

    public AuthenticatorException(String msg) {
        super(msg);
    }

    public AuthenticatorException(String msg, Throwable e) {
        super(msg, e);
    }
}
