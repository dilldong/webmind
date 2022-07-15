package org.mind.framework.compress.gzip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compress by GZIP stream
 *
 * @author dp
 */
public final class GZip {

    private static final Logger log = LoggerFactory.getLogger(GZip.class);

    public static String decompress(byte[] bytes) {
        try (GZIPInputStream in =
                     new GZIPInputStream(new ByteArrayInputStream(bytes));
             BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in))) {

            StringWriter writer = new StringWriter();
            String str = null;

            while ((str = reader.readLine()) != null)
                writer.write(str);

            return writer.toString();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        return new String(bytes);
    }

    public static byte[] compress(String str) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            GZIPOutputStream gzipOut = new GZIPOutputStream(out);

            gzipOut.write(str.getBytes(StandardCharsets.UTF_8));
            gzipOut.flush();
            gzipOut.close();
            return out.toByteArray();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return str.getBytes();
    }
}
