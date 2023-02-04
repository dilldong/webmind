package org.mind.framework.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * NIO Read/Write files
 *
 * @version 1.0
 * @auther Marcus
 * @date 2023/2/4
 */
@Slf4j
public class FileUtils {
    static final int MAX_BUFFER_SIZE = 1024;
    public static final String UNIX_DIR = String.valueOf(IOUtils.DIR_SEPARATOR_UNIX);
    public static final String WINDOWS_DIR = String.valueOf(IOUtils.DIR_SEPARATOR_WINDOWS);
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");

    /**
     * Write file
     *
     * @param directory file directory
     * @param fileName  file name
     * @param content   file content
     */
    public static void write(String directory, String fileName, String content) {
        createDirectory(directory);

        String filePath;
        if (directory.endsWith(IS_WINDOWS ? WINDOWS_DIR : UNIX_DIR))
            filePath = directory + fileName;
        else
            filePath = String.join(IS_WINDOWS ? WINDOWS_DIR : UNIX_DIR, directory, fileName);

        write(filePath, content);
    }

    /**
     * Write file
     *
     * @param filePath file absolute path
     * @param content  file content
     */
    public static void write(String filePath, String content) {
        write(Paths.get(filePath), content);
    }

    /**
     * Write file
     *
     * @param filePath file absolute path
     * @param content  file content
     */
    public static void write(Path filePath, String content) {
        if (log.isDebugEnabled())
            log.debug("Writing file path to: [{}]", filePath.toAbsolutePath());

        try (FileChannel channel = FileChannel.open(
                filePath,
                StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            channel.write(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            log.error("Failed to write file [{}], content: [{}], error: {}",
                    filePath.toAbsolutePath(),
                    content,
                    e.getMessage());
        }
    }

    /**
     * Read file
     *
     * @param filePath file absolute path
     * @return file content
     */
    public static String read(String filePath) {
        return read(Paths.get(filePath));
    }

    /**
     * Read file
     *
     * @param filePath     file absolute path
     * @param awaitAcquire The operation after the failure to get file lock, true: await to acquire file lock, false: break and return null
     * @return file content
     */
    public static String read(String filePath, boolean sharedLock, boolean awaitAcquire) {
        return read(Paths.get(filePath), sharedLock, awaitAcquire);
    }

    /**
     * Read file
     *
     * @param filePath file absolute path
     * @return file content
     */
    public static String read(Path filePath) {
        if (log.isDebugEnabled())
            log.debug("Reading file: [{}]", filePath.toAbsolutePath());

        BasicFileAttributes attributes = readAttributes(filePath);
        if (Objects.isNull(attributes))
            return null;

        long fileSize = attributes.size();
        if (!attributes.isRegularFile()) {
            log.warn("You reading file doesn't exist or is directory. [{}]", filePath.toAbsolutePath());
            return null;
        }

        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            return readBuffer(channel, fileSize);
        } catch (IOException e) {
            log.error("FileChannel open or read exception: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Read file with file lock
     *
     * @param filePath     file absolute path
     * @param sharedLock   true: shared lock, false: exclusive lock
     * @param awaitAcquire The operation after the failure to get file lock, true: await to acquire file lock, false: break and return null
     * @return file content
     */
    public static String read(Path filePath, boolean sharedLock, boolean awaitAcquire) {
        if (log.isDebugEnabled())
            log.debug("Reading file: [{}]", filePath.toAbsolutePath());

        BasicFileAttributes attributes = readAttributes(filePath);
        if (Objects.isNull(attributes))
            return null;

        long fileSize = attributes.size();
        if (!attributes.isRegularFile()) {
            log.warn("You reading file doesn't exist or is directory. [{}]", filePath.toAbsolutePath());
            return null;
        }

        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            int tryCounter = 0;
            do {
                try (FileLock lock = channel.tryLock(0, Long.MAX_VALUE, sharedLock)) {
                    if (Objects.isNull(lock))
                        throw new OverlappingFileLockException();

                    return readBuffer(channel, fileSize);
                } catch (OverlappingFileLockException e) {
                    log.warn("Get file lock overlap: {}", filePath.toAbsolutePath());
                    if(!awaitAcquire)
                        break;

                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException ex) {}
                }
            } while (++tryCounter < 10);
        } catch (IOException e) {
            log.error("FileChannel open or read exception: {}", e.getMessage());
        }
        return null;
    }

    private static String readBuffer(FileChannel channel, long size) throws IOException {
        if (size == -1L)
            size = MAX_BUFFER_SIZE;

        if (size > MAX_BUFFER_SIZE || size > Integer.MAX_VALUE)
            size = MAX_BUFFER_SIZE;

        ByteBuffer buffer = ByteBuffer.allocate((int) size);
        StringBuilder stringJoiner = new StringBuilder((int) size);
        while (channel.read(buffer) > -1) {
            buffer.flip();
            stringJoiner.append(
                    new String(buffer.array(),
                            buffer.position(),
                            buffer.limit(),
                            StandardCharsets.UTF_8));
            buffer.clear();
        }
        return stringJoiner.toString();
    }

    private static BasicFileAttributes readAttributes(Path filePath) {
        if (!Files.exists(filePath)) {
            log.warn("You reading file doesn't exist. [{}]", filePath.toAbsolutePath());
            return null;
        }

        try {
            return Files.readAttributes(filePath, BasicFileAttributes.class);
        } catch (IOException e) {
            log.warn("You reading file doesn't exist. [{}]", filePath.toAbsolutePath());
        }
        return null;
    }

    private static void createDirectory(String directory) {
        // Directory is exists
        Path path = Paths.get(directory);
        if (!(Files.exists(path) && Files.isDirectory(path))) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                log.error("Creating directory to failed, {}", e.getMessage());
            }
        }
    }
}
