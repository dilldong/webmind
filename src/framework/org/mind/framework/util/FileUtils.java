package org.mind.framework.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mind.framework.exception.ThrowProvider;

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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * NIO Read/Write files
 *
 * @version 1.0
 * @auther Marcus
 * @date 2023/2/4
 */
@Slf4j
public class FileUtils {
    public static final int MAX_BUFFER_SIZE = 1024;
    public static final String UNIX_DIR = String.valueOf(IOUtils.DIR_SEPARATOR_UNIX);
    public static final String WINDOWS_DIR = String.valueOf(IOUtils.DIR_SEPARATOR_WINDOWS);
    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");

    /**
     * Write file
     *
     * @param directory file directory
     * @param fileName  file name
     * @param content   file content
     * @return The number of bytes written, possibly zero
     */
    public static int write(String directory, String fileName, String content) {
        String filePath;
        if (directory.endsWith(IS_WINDOWS ? WINDOWS_DIR : UNIX_DIR))
            filePath = directory + fileName;
        else
            filePath = String.join(IS_WINDOWS ? WINDOWS_DIR : UNIX_DIR, directory, fileName);

        return write(filePath, content);
    }

    /**
     * Write file
     *
     * @param filePath file absolute path
     * @param content  file content
     * @return The number of bytes written, possibly zero
     */
    public static int write(String filePath, String content) {
        return write(Paths.get(filePath), content);
    }

    /**
     * Write file with file lock
     *
     * @param filePath     file absolute path
     * @param content      file content
     * @param sharedLock   true: shared lock, false: exclusive lock
     * @param awaitAcquire The operation after the failure to get file lock, true: await to acquire file lock, false: break and return null
     * @return The number of bytes written, possibly zero
     */
    public static int write(String filePath, String content, boolean sharedLock, boolean awaitAcquire) {
        return write(Paths.get(filePath), content, sharedLock, awaitAcquire);
    }

    /**
     * Write file
     *
     * @param filePath file absolute path
     * @param content  file content
     * @return The number of bytes written, possibly zero
     */
    public static int write(Path filePath, String content) {
        if (log.isDebugEnabled())
            log.debug("Writing file path to: [{}]", filePath.toAbsolutePath());

        if (!Files.exists(filePath.getParent())) {
            try {
                Files.createDirectories(filePath.getParent());
            } catch (IOException e) {
                log.error("Creating directory to failed, {}", e.getMessage());
                ThrowProvider.doThrow(e);
            }
        }

        try (FileChannel channel = FileChannel.open(
                filePath,
                StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            return channel.write(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            log.error("Failed to write file [{}], content: [{}], error: {}",
                    filePath.toAbsolutePath(),
                    content,
                    e.getMessage());
        }
        return 0;
    }

    /**
     * Write file with file lock
     *
     * @param filePath     file absolute path
     * @param content      file content
     * @param sharedLock   true: shared lock, false: exclusive lock
     * @param awaitAcquire The operation after the failure to get file lock, true: await to acquire file lock, false: break and return null
     * @return The number of bytes written, possibly zero
     */
    public static int write(Path filePath, String content, boolean sharedLock, boolean awaitAcquire) {
        if (log.isDebugEnabled())
            log.debug("Writing file path to: [{}]", filePath.toAbsolutePath());

        if (!Files.exists(filePath.getParent())) {
            try {
                Files.createDirectories(filePath.getParent());
            } catch (IOException e) {
                log.error("Creating directory to failed, {}", e.getMessage());
                ThrowProvider.doThrow(e);
            }
        }

        try (FileChannel channel =
                     FileChannel.open(filePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            int tryCounter = 0;
            do {
                // This method returns null or throws an exception if the file is already locked.
                try (FileLock lock = channel.tryLock(0, Long.MAX_VALUE, sharedLock)) {
                    if (Objects.isNull(lock))
                        throw new OverlappingFileLockException();

                    return channel.write(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
                } catch (OverlappingFileLockException e) {
                    log.warn("Write file lock overlap: {}", filePath.toAbsolutePath());
                    if (!awaitAcquire)
                        break;

                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException ignored) {
                    }
                    Thread.yield();
                }
            } while (++tryCounter < 10);
        } catch (IOException e) {
            log.error("Failed to write file [{}], content: [{}], error: {}",
                    filePath.toAbsolutePath(),
                    content,
                    e.getMessage());
        }
        return 0;
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
     * Read file with file lock
     *
     * @param filePath     file absolute path
     * @param sharedLock   true: shared lock, false: exclusive lock
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
     * @param awaitAcquire The operation after the failure to get file lock:
     *                     true: await to acquire file lock(also possible not get content),
     *                     false: break and return null
     * @return file content
     */
    public static String read(Path filePath, boolean sharedLock, boolean awaitAcquire) {
        return read(filePath, sharedLock, awaitAcquire ? 750L : 0L, TimeUnit.MILLISECONDS);
    }

    /**
     * @param filePath           file absolute path
     * @param sharedLock         true: shared lock, false: exclusive lock
     * @param awaitAcquireOfTime The waiting time for acquiring a lock (must be greater than 160 ms)
     * @param awaitTimeUnit      The unit of time to wait for a lock
     * @return file content
     */
    public static String read(Path filePath, boolean sharedLock, long awaitAcquireOfTime, TimeUnit awaitTimeUnit) {
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

        long loopLimit;
        if (awaitTimeUnit == TimeUnit.MILLISECONDS)
            loopLimit = awaitAcquireOfTime > 0L ? awaitAcquireOfTime / 160L : 0L;
        else
            loopLimit = awaitAcquireOfTime > 0L ? awaitTimeUnit.toMillis(awaitAcquireOfTime) / 160L : 0L;

        // get a file channel for the file
        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            do {
                // This method returns null or throws an exception if the file is already locked.
                try (FileLock lock = channel.tryLock(0, Long.MAX_VALUE, sharedLock)) {
                    if (Objects.isNull(lock))
                        throw new OverlappingFileLockException();

                    return readBuffer(channel, fileSize);
                } catch (OverlappingFileLockException e) {
                    log.warn("Get file lock overlap: {}", filePath.toAbsolutePath());
                    if (loopLimit == 0L)
                        break;

                    try {
                        Thread.sleep(160L);
                    } catch (InterruptedException ignored) {
                    }
                    Thread.yield();
                }
            } while (--loopLimit >= 0);
        } catch (IOException e) {
            log.error("FileChannel open or read exception: {}", e.getMessage());
        }
        return null;
    }

    public static List<Path> get(String directory) {
        return get(directory, null);
    }

    public static List<Path> get(String directory, @Nullable Function<Path, Boolean> filter) {
        try (Stream<Path> streams = Files.list(Paths.get(directory))) {
            if (Objects.isNull(filter))
                return streams.collect(Collectors.toList());

            return streams.filter(filter::apply).collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Read files is error, dir: {}, error: {}", directory, e.getMessage());
        }
        return Collections.emptyList();
    }

    public static void read(String directory, @NotNull Consumer<Path> content) {
        read(directory, null, content);
    }

    public static void read(String directory,
                            @Nullable Function<Path, Boolean> filter,
                            @NotNull Consumer<Path> content) {
        try (Stream<Path> streams = Files.list(Paths.get(directory))) {
            if (Objects.isNull(filter))
                streams.forEach(content);
            else
                streams.filter(filter::apply).forEach(content);
        } catch (IOException e) {
            log.error("Read files is error, dir: {}, error: {}", directory, e.getMessage());
        }
    }

    public static BasicFileAttributes readAttributes(Path filePath) {
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

    private static String readBuffer(FileChannel channel, long size) throws IOException {
        if (size == -1L || size > MAX_BUFFER_SIZE)
            size = MAX_BUFFER_SIZE;

        ByteBuffer buffer = ByteBuffer.allocate((int) size);
        StringBuilder stringJoiner = new StringBuilder(buffer.capacity());

        while (channel.read(buffer) > -1) {
            buffer.flip();
            stringJoiner.append(
                    new String(buffer.array(),
                            buffer.position(),
                            buffer.limit(),
                            StandardCharsets.UTF_8));
        }
        return stringJoiner.toString();
    }

}
