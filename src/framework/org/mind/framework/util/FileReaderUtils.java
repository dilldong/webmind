package org.mind.framework.util;

import java.io.IOException;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author: Marcus
 * @date: 2026/11/20
 * @version: 1.0
 */
public final class FileReaderUtils {

    // 小文件（默认 ≤ 4 MB）
    public static final int SMALL_FILE_LIMIT = 4 * 1024 * 1024;
    private static final int SMALL_BUFFER_SIZE = 8 * 1024;
    private static final int LARGE_BUFFER_SIZE = 64 * 1024;
    private static final int MAX_INITIAL_CAPACITY = 1024 * 1024;
    private static final int MAX_STRING_LENGTH = Integer.MAX_VALUE - 8;

    private FileReaderUtils() {
    }

    /**
     * Read the remaining content of a FileChannel as UTF-8.
     * Successful reads advance the channel to EOF. The caller owns the channel.
     * For large files that need not be loaded in full, use a streaming reader.
     *
     * @throws IOException if reading fails or the input contains malformed UTF-8
     * @see java.nio.file.Files#newBufferedReader(java.nio.file.Path)
     */
    public static String readBuffer(FileChannel channel) throws IOException {
        return readBuffer(channel, MAX_STRING_LENGTH);
    }

    /**
     * Read the remaining UTF-8 content, limited to the given number of UTF-16 code units.
     * The caller owns the channel. Successful reads advance it to EOF.
     *
     * @throws IllegalArgumentException if maxChars is negative
     * @throws IOException if reading or decoding fails, or the limit is exceeded
     */
    public static String readBuffer(FileChannel channel, int maxChars) throws IOException {
        Objects.requireNonNull(channel, "channel");
        if (maxChars < 0)
            throw new IllegalArgumentException("maxChars must be nonnegative");

        long remaining = Math.max(0L, channel.size() - channel.position());
        int bufferSize = remaining <= SMALL_FILE_LIMIT ? SMALL_BUFFER_SIZE : LARGE_BUFFER_SIZE;
        int initialCapacity = (int) Math.min(remaining, Math.min(MAX_INITIAL_CAPACITY, maxChars));
        StringBuilder result = new StringBuilder(initialCapacity);
        char[] buffer = new char[bufferSize];

        // Closing the reader would close the caller's channel.
        Reader reader = Channels.newReader(channel, StandardCharsets.UTF_8.newDecoder(), bufferSize);
        int count;

        while ((count = reader.read(buffer)) != -1) {
            if (count > maxChars - result.length())
                throw new IOException("File exceeds the limit of " + maxChars + " UTF-16 code units");

            result.append(buffer, 0, count);
        }
        return result.toString();
    }
}
