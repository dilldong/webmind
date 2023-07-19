package org.mind.framework.util;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * General purpose utility methods related to generating a servlet response in
 * the Action mind framework.
 *
 * @author dp
 */
public class ResponseUtils {

    static final int MAX_BUFFER_SIZE = 1024;

    /**
     * Filter the specified string for characters that are sensitive to HTML
     * interpreters, returning the string with these characters replaced by the
     * corresponding character entities.
     *
     * @param value The string to be filtered and returned
     */
    public static String filter(String value) {
        if (StringUtils.isEmpty(value))
            return null;

        char[] content = new char[value.length()];
        value.getChars(0, value.length(), content, 0);
        StringBuilder result = new StringBuilder(content.length + 50);

        for (char ch : content) {
            switch (ch) {
                case '<':
                    result.append("&lt;");
                    break;
                case '>':
                    result.append("&gt;");
                    break;
                case '&':
                    result.append("&amp;");
                    break;
                case '"':
                    result.append("&quot;");
                    break;
                case '\'':
                    result.append("&#39;");
                    break;
                default:
                    result.append(ch);
            }
        }
        return result.toString();

    }


    /**
     * Output {@link Character} stream. support GBK
     *
     * @param writer
     * @param value
     * @throws IOException
     */
    public static void write(PrintWriter writer, String value) throws IOException {
        writer.write(value);
        writer.flush();
    }

    /**
     * Output {@link Character} array stream. support GBK
     *
     * @param writer
     * @param value
     * @throws IOException
     */
    public static void write(PrintWriter writer, char[] value) throws IOException {
        writer.write(value);
        writer.flush();
    }

    /**
     * Output {@link Byte} stream.
     *
     * @param output
     * @param bytes
     * @throws IOException
     */
    public static void write(OutputStream output, byte[] bytes) throws IOException {
        output.write(bytes);
        output.flush();
    }


    /**
     * Output {@link File} stream IO.
     *
     * @param output
     * @param file
     * @throws IOException
     */
    public static void write(OutputStream output, File file) throws IOException {
        write(output, file.toPath());
    }

    public static void write(OutputStream output, Path path) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] buffer = new byte[FileUtils.MAX_BUFFER_SIZE];

            int length;
            while ((length = input.read(buffer)) > -1)
                output.write(buffer, 0, length);

            output.flush();
        }
    }

}
