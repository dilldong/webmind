package org.mind.framework.exception;

/**
 * 拦截检查每个到达受保护的资源,接受用户名和密码，通过用户的资格系统验证用户
 *
 * @author dongping
 */
public class AuthenticatorException extends BaseException {

    private static final long serialVersionUID = -4294788851970815693L;

    /**
     * @param msg
     * @author dongping
     */
    public AuthenticatorException(String msg) {
        super(msg);
    }


    /**
     * @param msg
     * @param e
     * @author dongping
     */
    public AuthenticatorException(String msg, Throwable e) {
        super(msg, e);
    }

    @Override
    public int getErrLevel() {
        return super.getErrLevel();
    }


}
