package org.mind.framework.compress.gzip;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Compress by GZIP stream
 *
 * @author dp
 */
public class GZip {

    private static final Log log = LogFactory.getLog(GZip.class);

    public static String decompress(byte[] bytes) {
        try {
            GZIPInputStream in =
                    new GZIPInputStream(new ByteArrayInputStream(bytes));

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(in));

            StringWriter writer = new StringWriter();
            String str = null;

            while ((str = reader.readLine()) != null) {
                writer.write(str);
            }

            reader.close();
            String result = writer.toString();
            writer.close();
            return result;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        return new String(bytes);
    }

    public static byte[] compress(String str) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream gzipOut = new GZIPOutputStream(out);
            gzipOut.write(str.getBytes("UTF-8"));
            gzipOut.flush();
            gzipOut.close();
            return out.toByteArray();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return str.getBytes();
    }

}
