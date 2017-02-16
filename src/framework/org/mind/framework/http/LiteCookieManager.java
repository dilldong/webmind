package org.mind.framework.http;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.mind.framework.container.Destroyable;

/**
 * CookieManager is a simple utilty for handling cookies when working with
 * java.net.URL and java.net.URLConnection objects.
 * 
 * 
 * Cookiemanager cm = new CookieManager(); URL url = new
 * URL("http://www.hccp.org/test/cookieTest.jsp");
 * 
 * . . .
 * 
 * // getting cookies: URLConnection conn = url.openConnection();
 * conn.connect();
 * 
 * // setting cookies cm.storeCookies(conn);
 * cm.setCookies(url.openConnection());
 * 
 * @author Ian Brown
 * 
 */
public class LiteCookieManager implements Destroyable{
	private static final Logger logger = Logger.getLogger(LiteCookieManager.class);
	private Map<String, Map<String, Map<String, String>>> store;

	private static final String SET_COOKIE = "Set-Cookie";
	private static final String COOKIE_VALUE_DELIMITER = ";";
	private static final String PATH = "path";
	private static final String EXPIRES = "expires";
	private static final String DATE_FORMAT = "EEE, dd-MMM-yyyy hh:mm:ss z";
	private static final String SET_COOKIE_SEPARATOR = "; ";
	private static final String COOKIE = "Cookie";

	private static final char NAME_VALUE_SEPARATOR = '=';
	private static final char DOT = '.';

	public LiteCookieManager() {
		store = new HashMap<String, Map<String, Map<String, String>>>();
	}
	

	/**
	 * Retrieves and stores cookies returned by the host on the other side of
	 * the the open java.net.URLConnection.
	 * 
	 * The connection MUST have been opened using the connect() method or a
	 * IOException will be thrown.
	 * 
	 * @param conn
	 *            a java.net.URLConnection - must be open, or IOException will
	 *            be thrown
	 * @throws java.io.IOException
	 *             Thrown if conn is not open.
	 */
	public void storeCookies(URLConnection conn) throws IOException {

		// let's determine the domain from where these cookies are being sent
		String domain = getDomainFromHost(conn.getURL().getHost());

		Map<String, Map<String, String>> domainStore; // this is where we will store cookies for this domain

		// now let's check the store to see if we have an entry for this domain
		if (store.containsKey(domain)) {
			// we do, so lets retrieve it from the store
			domainStore = store.get(domain);
		} else {
			// we don't, so let's create it and put it in the store
			domainStore = new HashMap<String, Map<String, String>>();
			store.put(domain, domainStore);
		}

		// OK, now we are ready to get the cookies out of the URLConnection

		String headerName = null;
		for (int i = 1; (headerName = conn.getHeaderFieldKey(i)) != null; i++) {
			if (headerName.equalsIgnoreCase(SET_COOKIE)) {
				Map<String, String> cookie = new HashMap<String, String>();
				StringTokenizer st = new StringTokenizer(
						conn.getHeaderField(i), COOKIE_VALUE_DELIMITER);

				// the specification dictates that the first name/value pair
				// in the string is the cookie name and value, so let's handle
				// them as a special case:

				if (st.hasMoreTokens()) {
					String token = st.nextToken();
					String name = token.substring(0,
							token.indexOf(NAME_VALUE_SEPARATOR));
					String value = token.substring(
							token.indexOf(NAME_VALUE_SEPARATOR) + 1,
							token.length());
					domainStore.put(name, cookie);
					cookie.put(name, value);
				}

				while (st.hasMoreTokens()) {
					String token = st.nextToken();
					cookie.put(
							token.substring(0,
									token.indexOf(NAME_VALUE_SEPARATOR))
									.toLowerCase(), token.substring(
									token.indexOf(NAME_VALUE_SEPARATOR) + 1,
									token.length()));
				}
			}
		}
	}

	/**
	 * Prior to opening a URLConnection, calling this method will set all
	 * unexpired cookies that match the path or subpaths for thi underlying URL
	 * 
	 * The connection MUST NOT have been opened method or an IOException will be
	 * thrown.
	 * 
	 * @param conn
	 *            a java.net.URLConnection - must NOT be open, or IOException
	 *            will be thrown
	 * @throws java.io.IOException
	 *             Thrown if conn has already been opened.
	 */
	public void setCookies(URLConnection conn) throws IOException {

		// let's determine the domain and path to retrieve the appropriate
		// cookies
		URL url = conn.getURL();
		String domain = getDomainFromHost(url.getHost());
		String path = url.getPath();

		Map<String, Map<String, String>> domainStore = store.get(domain);
		if (domainStore == null)
			return;
		
		StringBuffer cookieStringBuffer = new StringBuffer();

		Iterator<String> cookieNames = domainStore.keySet().iterator();
		while (cookieNames.hasNext()) {
			String cookieName = (String) cookieNames.next();
			Map<String, String> cookie = domainStore.get(cookieName);
			// check cookie to ensure path matches and cookie is not expired
			// if all is cool, add cookie to header string
			if (comparePaths((String) cookie.get(PATH), path)
					&& isNotExpired((String) cookie.get(EXPIRES))) {
				cookieStringBuffer.append(cookieName);
				cookieStringBuffer.append("=");
				cookieStringBuffer.append((String) cookie.get(cookieName));
				if (cookieNames.hasNext())
					cookieStringBuffer.append(SET_COOKIE_SEPARATOR);
			}
		}
		try {
			logger.info("set-cookie: "+ cookieStringBuffer.toString());
			conn.setRequestProperty(COOKIE, cookieStringBuffer.toString());
		} catch (java.lang.IllegalStateException ise) {
			IOException ioe = new IOException(
					"Illegal State! Cookies cannot be set on a URLConnection that is already connected. "
							+ "Only call setCookies(java.net.URLConnection) AFTER calling java.net.URLConnection.connect().");
			throw ioe;
		}
	}
	
	/**
	 * Clear stories Cookie by domain.
	 * @param conn
	 * 			<br>a java.net.URLConnection - must NOT be open, or IOException
	 *            will be thrown
	 */
	public void clear(URLConnection conn){
		String domain = getDomainFromHost(conn.getURL().getHost());
		
		if(this.containsCookie(domain))
			this.store.remove(domain);
	}
	
	public boolean containsCookie(URLConnection conn){
		String domain = getDomainFromHost(conn.getURL().getHost());
		return this.containsCookie(domain);
	}
	
	public boolean containsCookie(String domain){
		return this.store.containsKey(domain);
	}

	private String getDomainFromHost(String host) {
		if (host.indexOf(DOT) != host.lastIndexOf(DOT))
			return host.substring(host.indexOf(DOT) + 1);
			
		return host;
	}
	

	private boolean isNotExpired(String cookieExpires) {
		if (cookieExpires == null)
			return true;
		
		Date now = new Date();
		try {
			return (now.compareTo(new SimpleDateFormat(DATE_FORMAT).parse(cookieExpires))) <= 0;
		} catch (java.text.ParseException pe) {
			logger.error(pe.getMessage(), pe);
			return false;
		}
	}

	private boolean comparePaths(String cookiePath, String targetPath) {
		if (cookiePath == null) {
			return true;
		} else if (cookiePath.equals("/")) {
			return true;
		} else if (targetPath.regionMatches(0, cookiePath, 0, cookiePath.length())) {
			return true;
		} else {
			return false;
		}

	}

	/**
	 * Returns a string representation of stored cookies organized by domain.
	 */

	public String toString() {
		return store.toString();
	}

	@Override
	public void destroy() {
		if(this.store == null || this.store.isEmpty())
			return;
		
		this.store.clear();
		this.store = null;
	}
}
