package org.mind.framework.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.Strings;
import org.mind.framework.exception.ThrowProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
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
 * @author Marcus
 * @date 2023/2/4
 */
@Slf4j
public class FileUtils {
    public static final int BUFFER_SIZE = 8192;// 8Kb buffer size
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
    public static long write(String directory, String fileName, String content) {
        String filePath;
        if (directory.endsWith(IS_WINDOWS ? WINDOWS_DIR : UNIX_DIR))
            filePath = directory + fileName;
        else
            filePath = String.join(IS_WINDOWS ? WINDOWS_DIR : UNIX_DIR, directory, fileName);

        return write(filePath, content);
    }

    public static long write(String directory, String fileName, InputStream content) {
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
    public static long write(String filePath, String content) {
        return write(Paths.get(filePath), content);
    }

    public static long write(String filePath, InputStream content) {
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
    public static long write(String filePath, String content, boolean sharedLock, boolean awaitAcquire) {
        return write(Paths.get(filePath), content, sharedLock, awaitAcquire);
    }

    public static long write(String filePath, InputStream content, boolean sharedLock, boolean awaitAcquire) {
        return write(Paths.get(filePath), content, sharedLock, awaitAcquire);
    }

    /**
     * Write file
     *
     * @param filePath file absolute path
     * @param content  file content
     * @return The number of bytes written, possibly zero
     */
    public static long write(Path filePath, String content) {
        log.debug("Writing file path to: [{}]", filePath.toAbsolutePath());

        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            log.error("Creating directory to failed, {}", e.getMessage());
            ThrowProvider.doThrow(e);
        }

        try (FileChannel channel = FileChannel.open(
                filePath,
                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            return writtenString(channel, content);
        } catch (IOException e) {
            log.error("Failed to write file [{}], content: [{}], error: {}",
                    filePath.toAbsolutePath(),
                    content,
                    e.getMessage());
        }
        return 0L;
    }

    public static long write(Path filePath, InputStream content) {
        log.debug("Writing file path to: [{}]", filePath.toAbsolutePath());

        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            log.error("Creating directory to failed, {}", e.getMessage());
            ThrowProvider.doThrow(e);
        }

        try (FileChannel channel = FileChannel.open(
                filePath,
                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            return writtenStream(channel, content);
        } catch (IOException e) {
            log.error("Failed to write file [{}], error: {}",
                    filePath.toAbsolutePath(),
                    e.getMessage());
        }
        return 0L;
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
    public static long write(Path filePath, String content, boolean sharedLock, boolean awaitAcquire) {
        log.debug("Writing file path to: [{}]", filePath.toAbsolutePath());

        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            log.error("Creating directory to failed, {}", e.getMessage());
            ThrowProvider.doThrow(e);
        }

        try (FileChannel channel = FileChannel.open(filePath,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            int tryCounter = 0;
            do {
                // This method returns null or throws an exception if the file is already locked.
                try (FileLock lock = channel.tryLock(0, Long.MAX_VALUE, sharedLock)) {
                    if (Objects.isNull(lock)) {
                        if (!awaitAcquire) {
                            log.warn("File lock not acquired and await disabled: {}", filePath);
                            return 0L;
                        }
                        log.debug("File is locked, waiting....");
                    } else {
                        return writtenString(channel, content);
                    }
                } catch (OverlappingFileLockException e) {
                    log.warn("Overlapping lock detected: {}", filePath.toAbsolutePath());
                    if (!awaitAcquire)
                        return 0L;
                }

                try {
                    long sleepTime = 100L + (1L << tryCounter);
                    Thread.sleep(Math.min(sleepTime, 1_000L));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for file lock");
                }
            } while (++tryCounter < 10);

            log.warn("Failed to acquire lock after {} attempts", tryCounter);
        } catch (IOException e) {
            String abbreviatedContent = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            log.error("Failed to write file [{}], content: [{}], error: {}",
                    filePath.toAbsolutePath(),
                    abbreviatedContent,
                    e.getMessage());
            ThrowProvider.doThrow(e);
        }
        return 0L;
    }

    public static long write(Path filePath, InputStream content, boolean sharedLock, boolean awaitAcquire) {
        log.debug("Writing file path to: [{}]", filePath.toAbsolutePath());

        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            log.error("Creating directory to failed, {}", e.getMessage());
            ThrowProvider.doThrow(e);
        }

        try (FileChannel channel = FileChannel.open(filePath,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            int tryCounter = 0;
            do {
                // This method returns null or throws an exception if the file is already locked.
                try (FileLock lock = channel.tryLock(0, Long.MAX_VALUE, sharedLock)) {
                    if (Objects.isNull(lock)) {
                        if (!awaitAcquire) {
                            log.warn("File lock not acquired and await disabled: {}", filePath);
                            return 0L;
                        }
                        log.debug("File is locked, waiting unlock....");
                    } else {
                        return writtenStream(channel, content);
                    }
                } catch (OverlappingFileLockException e) {
                    log.warn("Overlapping lock detected: {}", filePath.toAbsolutePath());
                    if (!awaitAcquire)
                        return 0L;
                }

                try {
                    long sleepTime = 100L + (1L << tryCounter);
                    Thread.sleep(Math.min(sleepTime, 1_000L));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for file unlock");
                }
            } while (++tryCounter < 10);

            log.warn("Failed to acquire lock after {} attempts", tryCounter);
        } catch (IOException e) {
            log.error("Failed to write file [{}], error: {}",
                    filePath.toAbsolutePath(),
                    e.getMessage());
        }
        return 0L;
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
        log.debug("Reading file: [{}]", filePath.toAbsolutePath());

        BasicFileAttributes attributes = readAttributes(filePath);
        if (Objects.isNull(attributes))
            return null;

        if (!attributes.isRegularFile()) {
            log.warn("You reading file doesn't exist or is directory. [{}]", filePath.toAbsolutePath());
            return null;
        }

        long fileSize = attributes.size();
        if (fileSize < 1)
            return StringUtils.EMPTY;

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
        log.debug("Reading file: [{}]", filePath.toAbsolutePath());

        BasicFileAttributes attributes = readAttributes(filePath);
        if (Objects.isNull(attributes))
            return null;

        if (!attributes.isRegularFile()) {
            log.warn("You reading file doesn't exist or is directory. [{}]", filePath.toAbsolutePath());
            return null;
        }

        long fileSize = attributes.size();
        if (fileSize < 1)
            return StringUtils.EMPTY;

        long loopLimit;
        if (awaitTimeUnit == TimeUnit.MILLISECONDS)
            loopLimit = awaitAcquireOfTime > 0L ? awaitAcquireOfTime / 160L : 0L;
        else
            loopLimit = awaitAcquireOfTime > 0L ? awaitTimeUnit.toMillis(awaitAcquireOfTime) / 160L : 0L;

        // get a file channel for the file
        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            int tryCounter = 0;
            do {
                // This method returns null or throws an exception if the file is already locked.
                try (FileLock lock = channel.tryLock(0, Long.MAX_VALUE, sharedLock)) {
                    if (Objects.isNull(lock)) {
                        if (loopLimit == 0L)
                            break;
                        log.debug("File is locked, waiting unlock....");
                    } else {
                        return readBuffer(channel, fileSize);
                    }
                } catch (OverlappingFileLockException e) {
                    log.warn("Get file lock overlap: {}", filePath.toAbsolutePath());
                    if (loopLimit == 0L)
                        break;
                }

                try {
                    long sleepTime = 100L + (1L << (++loopLimit));
                    Thread.sleep(Math.min(sleepTime, 1_000L));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for file unlock");
                }

            } while (--loopLimit >= 0);

            log.warn("Failed to acquire lock after {} attempts", tryCounter);
        } catch (IOException e) {
            log.error("Open or read file [{}] exception: {}",
                    filePath.toAbsolutePath(), e.getMessage());
        }
        return null;
    }

    public static List<Path> get(String directory) {
        return get(directory, null);
    }

    public static List<Path> get(String directory, Function<Path, Boolean> filter) {
        try (Stream<Path> streams = Files.list(Paths.get(directory))) {
            if (Objects.isNull(filter))
                return streams.collect(Collectors.toList());

            return streams.filter(filter::apply).collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Read files is error, dir: {}, error: {}", directory, e.getMessage());
        }
        return Collections.emptyList();
    }

    public static void read(String directory, Consumer<Path> content) {
        read(directory, null, content);
    }

    public static void read(String directory,
                            Function<Path, Boolean> filter,
                            Consumer<Path> content) {
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

    private static String readBuffer(FileChannel channel, long fileSize) throws IOException {
        boolean nonRange = fileSize < 1L || fileSize > BUFFER_SIZE;

        ByteBuffer buffer = ByteBuffer.allocate(nonRange ? BUFFER_SIZE : (int) fileSize);// Direct ByteBuffer
        StringBuilder strBuilder = new StringBuilder((int) Math.min(fileSize, Integer.MAX_VALUE - 1));

        while (channel.read(buffer) > -1) {
            buffer.flip();
            strBuilder.append(Strings.fromUTF8ByteArray(
                    buffer.array(),
                    buffer.position(),
                    buffer.limit()));
            buffer.clear();
        }
        return strBuilder.toString();
    }

    private static long writtenString(FileChannel channel, String content) throws IOException {
        // 字符长度小于BUFFER_SIZE, 直接写入
        // 当长度超过BUFFER_SIZE后，再进行分段写入
        int totalLength = content.length();
        if(totalLength <= BUFFER_SIZE){
            ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
            return channel.write(buffer);
        }

        // 初始化编码组件
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        CharBuffer charBuffer = CharBuffer.wrap(content);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

        // 分块编码循环
        CoderResult result;
        long totalWritten = 0L;

        do {
            result = encoder.encode(charBuffer, byteBuffer, false);
            if (result.isOverflow())
                totalWritten += flushBuffer(channel, byteBuffer);
            else if (result.isError())
                handleEncodingError(result);
        } while (result.isOverflow());

        // 处理最后的数据
        encoder.encode(charBuffer, byteBuffer, true);
        totalWritten += flushBuffer(channel, byteBuffer);

        channel.force(true);// Forced flash to ensure data persistence
        return totalWritten;
    }

    private static void handleEncodingError(CoderResult result) throws CharacterCodingException {
        if (result.isMalformed()) {
            throw new MalformedInputException(result.length());
        } else if (result.isUnmappable()) {
            throw new UnmappableCharacterException(result.length());
        }
    }

    private static int flushBuffer(FileChannel channel, ByteBuffer buffer) throws IOException {
        int totalWritten = 0;
        buffer.flip();
        while (buffer.hasRemaining()) {
            int written = channel.write(buffer);
            if (written < 0)
                break;
            totalWritten += written;
        }
        buffer.clear();
        return totalWritten;
    }

    private static long writtenStream(FileChannel channel, InputStream inputStream) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);// Direct ByteBuffer
        byte[] tempArray = new byte[BUFFER_SIZE];

        int bytesRead;
        long totalWritten = 0L;
        while ((bytesRead = inputStream.read(tempArray)) != -1) {
            buffer.clear();
            buffer.put(tempArray, 0, bytesRead);
            buffer.flip();

            while (buffer.hasRemaining()) {
                int written = channel.write(buffer);
                if (written < 0)
                    break;
                totalWritten += written;
            }
        }
        channel.force(true); // Forced flash to ensure data persistence
        return totalWritten;
    }
}
