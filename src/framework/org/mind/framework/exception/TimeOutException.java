package org.mind.framework.exception;

/**
 * 该异常类主要用于系统在检查session、session中的用户
 * 是否过期，如果抛出该异常，系统应自动导向到提示页面，
 * 告诉用户重新登录。
 * <br><b>注意:该异常是非受检异常</b>
 *
 * @author dongping
 */
public class TimeOutException extends RuntimeException {

    private static final long serialVersionUID = -7510369355196638420L;

    /**
     * @author dongping
     */
    public TimeOutException() {
        super();
    }

    /**
     * @param message
     * @param cause
     * @author dongping
     */
    public TimeOutException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @author dongping
     */
    public TimeOutException(String message) {
        super(message);
    }

    /**
     * @param cause
     * @author dongping
     */
    public TimeOutException(Throwable cause) {
        super(cause);
    }


}
