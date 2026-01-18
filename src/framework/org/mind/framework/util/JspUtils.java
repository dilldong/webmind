package org.mind.framework.util;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.tagext.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author dp
 */
public final class JspUtils extends ResponseUtils {

    private static final Logger logger = LoggerFactory.getLogger(JspUtils.class);

    /**
     * 通过PageContext中的JspWriter输出，并换行
     *
     * @param pageContext
     * @param text
     * @author dp
     * @date Jun 13, 2010
     */
    public static void writeln(PageContext pageContext, String text) {
        JspWriter writer = pageContext.getOut();
        try {
            writer.println(text);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * 使用HttpServletResponse中的PrintWriter输出
     *
     * @param response
     * @param text
     * @author dp
     */
    public static void print(HttpServletResponse response, String text) {
        try {
            print(response.getWriter(), text);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * PrintWriter输出
     *
     * @param out
     * @param text
     * @author dp
     * @date Jun 13, 2010
     */
    public static void print(PrintWriter out, String text) {
        if (out == null) {
            logger.error("PrintWriter object does not exist.");
            return;
        }

        if (text == null) {
            logger.error("To output the object does not exist.");
            return;
        }

        try {
            out.print(text);
        } finally {
            out.flush();
            out.close();
        }
    }

    public static Tag getParent(Tag self, Class<?> clazz) throws JspException {
        Tag tag = self.getParent();
        while (!(clazz.isAssignableFrom(tag.getClass()))) {
            tag = tag.getParent();
            if (tag == null) {
                String message = String.format("Parent tag of class %s of the tag's class %s was not found.", clazz.getName(), self.getClass().getName());
                throw new JspException(message);
            }
        }
        return tag;
    }

}
