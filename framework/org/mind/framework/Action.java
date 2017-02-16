package org.mind.framework;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Holds all Servlet objects in ThreadLocal.
 * 
 * @author dp
 */
public final class Action {

    private static final ThreadLocal<Action> actionContext = 
    		new ThreadLocal<Action>();

    private ServletContext context;
    private HttpServletRequest request;
    private HttpServletResponse response;
    
    /**
    * Web 服务器反向代理中用于存放客户端原始 IP 地址的 Http header 名字，
    * 若新增其他的需要增加或者修改其中的值。
    */
    private static final String[] PROXY_REMOTE_IP_ADDRESS = { "X-Forwarded-For", "X-Real-IP" };
    
    public String getRemoteIp(){
    	String ip = this.request.getHeader(PROXY_REMOTE_IP_ADDRESS[0]);
    	if(ip != null && ip.trim().length() > 0)
			return this.getRemoteIpFromForward(ip);
    	
    	ip = this.request.getHeader(PROXY_REMOTE_IP_ADDRESS[1]);
    	if(ip != null && ip.trim().length() > 0)
    		return ip;
    	
    	return this.request.getRemoteAddr();
    }
    
    /**
     * 从 HTTP Header 中截取客户端连接 IP 地址。如果经过多次反向代理，
     * 在请求头中获得的是以“,&lt;SP&gt;”分隔 IP 地址链，第一段为客户端 IP 地址。
     *
     * @return 客户端源 IP 地址
     */
    private String getRemoteIpFromForward(String xforwardIp) {
    	//从 HTTP 请求头中获取转发过来的 IP 地址链
        int commaOffset = xforwardIp.indexOf( ',' );
        if ( commaOffset < 0 )
            return xforwardIp;
        
        return xforwardIp.substring( 0 , commaOffset );
    }
    
    /**
     * Return the ServletContext of current web application.
     */
    public ServletContext getServletContext() {
        return context;
    }

    /**
     * Return current request object.
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * Return current response object.
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * Return current session object.
     */
    public HttpSession getSession() {
        return request.getSession();
    }

    /**
     * Get current Action object.
     */
    public static Action getActionContext() {
        return actionContext.get();
    }

    public static void setActionContext(ServletContext context, HttpServletRequest request, HttpServletResponse response) {
        Action ctx = new Action();
        ctx.context = context;
        ctx.request = request;
        ctx.response = response;
        actionContext.set(ctx);
    }

    public static void removeActionContext() {
        actionContext.remove();
    }
}
