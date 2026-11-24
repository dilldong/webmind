package org.mind.framework.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * NIO Read/Write files.
 * Writes stage data in the target directory before publication. Overwrites require an atomic move.
 * First-time locked publication and coordinator cleanup share the stable .lifecycle.lock in
 * the reserved .fus-create-locks subdirectory. Keep the lifecycle lock file in place, and
 * use the same protocol in every publishing process before opening or removing a coordinator.
 *
 * @author Marcus
 * @version 1.1
 * @date 2023/2/4
 */
@Slf4j
public class FileUtils {
    public static final int BUFFER_SIZE = 8192;// 8Kb buffer size
    public static final String UNIX_DIR = String.valueOf(IOUtils.DIR_SEPARATOR_UNIX);
    public static final String WINDOWS_DIR = String.valueOf(IOUtils.DIR_SEPARATOR_WINDOWS);
    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");

    private static final long NO_FILE_LOCK = -1L;
    private static final long READ_LOCK_TIMEOUT_MILLIS = 750L;
    private static final long WRITE_LOCK_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(2L);
    private static final long LOCK_RETRY_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(10L);
    private static final String CREATION_LOCK_DIRECTORY = ".fus-create-locks";
    private static final String CREATION_LIFECYCLE_LOCK = ".lifecycle.lock";
    private static final ConcurrentMap<Path, LocalFileAccess> FILE_ACCESS = new ConcurrentHashMap<>();

    /**
     * Write UTF-8 text to a file.
     *
     * @param directory file directory
     * @param fileName  file name
     * @param content   file content
     * @return the number of bytes written
     * @throws UncheckedIOException if the write fails
     */
    public static long write(String directory, String fileName, String content) {
        return write(Paths.get(directory, fileName), content);
    }

    /**
     * Write bytes to a file. The caller is responsible for closing the input stream.
     */
    public static long write(String directory, String fileName, InputStream content) {
        return write(Paths.get(directory, fileName), content);
    }

    public static long write(String filePath, String content) {
        return write(Paths.get(filePath), content);
    }

    /**
     * Write bytes to a file. The caller is responsible for closing the input stream.
     */
    public static long write(String filePath, InputStream content) {
        return write(Paths.get(filePath), content);
    }

    /**
     * Write UTF-8 text with an exclusive lock. See {@link #write(Path, String, boolean, boolean)}.
     */
    public static long write(String filePath, String content, boolean sharedLock, boolean awaitAcquire) {
        return write(Paths.get(filePath), content, sharedLock, awaitAcquire);
    }

    /**
     * Write bytes with an exclusive lock. The caller is responsible for closing the input stream.
     */
    public static long write(String filePath, InputStream content, boolean sharedLock, boolean awaitAcquire) {
        return write(Paths.get(filePath), content, sharedLock, awaitAcquire);
    }

    /**
     * Overwrite a file with UTF-8 text.
     *
     * @return the number of bytes written
     * @throws UncheckedIOException if the write fails
     */
    public static long write(Path filePath, String content) {
        Objects.requireNonNull(content, "content");
        return write(filePath, NO_FILE_LOCK, channel -> writtenString(channel, content));
    }

    /**
     * Overwrite a file with bytes from the supplied stream. The caller owns the stream.
     *
     * @return the number of bytes written
     * @throws UncheckedIOException if the write fails
     */
    public static long write(Path filePath, InputStream content) {
        Objects.requireNonNull(content, "content");
        return write(filePath, NO_FILE_LOCK, channel -> writtenStream(channel, content));
    }

    /**
     * Overwrite a file with UTF-8 text while holding an exclusive lock.
     *
     * @param sharedLock   must be false
     * @param awaitAcquire whether to wait up to two seconds to acquire the lock
     * @return the number of bytes written, or zero if the lock could not be acquired
     * @throws IllegalArgumentException if a shared lock is requested
     * @throws UncheckedIOException     if the write fails
     */
    public static long write(Path filePath, String content, boolean sharedLock, boolean awaitAcquire) {
        requireExclusiveWriteLock(sharedLock);
        Objects.requireNonNull(content, "content");

        return write(
                filePath,
                awaitAcquire ? WRITE_LOCK_TIMEOUT_NANOS : 0L,
                channel -> writtenString(channel, content)
        );
    }

    /**
     * Overwrite a file while holding an exclusive lock. The caller owns the stream.
     *
     * @param sharedLock   must be false
     * @param awaitAcquire whether to wait up to two seconds to acquire the lock
     * @return the number of bytes written, or zero if the lock could not be acquired
     * @throws IllegalArgumentException if a shared lock is requested
     * @throws UncheckedIOException     if the write fails
     */
    public static long write(Path filePath, InputStream content, boolean sharedLock, boolean awaitAcquire) {
        requireExclusiveWriteLock(sharedLock);
        Objects.requireNonNull(content, "content");

        return write(
                filePath,
                awaitAcquire ? WRITE_LOCK_TIMEOUT_NANOS : 0L,
                channel -> writtenStream(channel, content)
        );
    }

    private static void requireExclusiveWriteLock(boolean sharedLock) {
        if (sharedLock)
            throw new IllegalArgumentException("Writes require an exclusive file lock");
    }

    private static long write(Path filePath, long lockTimeoutNanos, ChannelWriter writer) {
        log.debug("Writing file: [{}]", filePath.toAbsolutePath());
        boolean lockRequired = lockTimeoutNanos != NO_FILE_LOCK;

        try {
            return withFileAccess(filePath, lockTimeoutNanos, 0L, (target, deadline) -> {
                Path directory = target.getParent();
                if (directory == null)
                    throw new FileSystemException(target.toString(), null, "Expected a regular file");

                Files.createDirectories(directory);
                if (!lockRequired) {
                    checkWritableTarget(target);
                    return writeStaged(target, writer, false, null, deadline);
                }

                LockedFile existing;
                try {
                    existing = openLockedFile(target, false, deadline, StandardOpenOption.WRITE);
                } catch (NoSuchFileException e) {
                    return writeStaged(target, writer, true, null, deadline);
                }
                if (existing == null) {
                    log.debug("Could not acquire write lock for [{}]", target);
                    return 0L;
                }
                try (existing) {
                    return writeStaged(target, writer, true, existing, deadline);
                } finally {
                    cleanLegacyCreationLock(target);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write file " + filePath.toAbsolutePath(), e);
        }
    }

    private static long writeStaged(Path target,
                                    ChannelWriter writer,
                                    boolean lockRequired,
                                    LockedFile existing,
                                    long deadline) throws IOException {
        Path staged = Files.createTempFile(target.getParent(), ".fus-", ".tmp");

        try {
            long written;
            try (FileChannel output = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                written = writer.write(output);
                output.force(true);
            }

            if (!lockRequired) {
                replaceAtomically(staged, target, checkWritableTarget(target));
            } else if (!publishLocked(staged, target, existing, deadline)) {
                Files.deleteIfExists(staged);
                return 0L;
            }
            return written;
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(staged);
            } catch (IOException | SecurityException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static boolean publishLocked(Path staged,
                                         Path target,
                                         LockedFile existing,
                                         long deadline) throws IOException {
        // Keep the replacement locked across the rename. The old lock protects the old file.
        try (LockedFile replacement = openLockedFile(staged, false, System.nanoTime(), StandardOpenOption.WRITE)) {
            if (replacement == null)
                throw new FileSystemException(staged.toString(), null, "Could not lock the staged file");

            if (existing != null) {
                replaceLocked(staged, existing);
                return true;
            }

            return publishFirstLocked(staged, target, deadline);
        }
    }

    private static boolean publishFirstLocked(Path staged, Path target, long deadline) throws IOException {
        if (target.getFileName().toString().equalsIgnoreCase(CREATION_LOCK_DIRECTORY))
            throw new FileSystemException(target.toString(), null, "Name is reserved for creation locks");

        Path lockDirectory = target.getParent().resolve(CREATION_LOCK_DIRECTORY);
        Files.createDirectories(lockDirectory);
        Path lifecycle = lockDirectory.resolve(CREATION_LIFECYCLE_LOCK);

        do {
            if (withCreationLifecycle(lifecycle, deadline, (path, ignored) -> tryPublishFirst(staged, target, path)))
                return true;
        } while (waitForLockRetry(deadline));

        return false;
    }

    private static boolean withCreationLifecycle(Path lifecycle, long deadline, FileOperation<Boolean> operation) throws IOException {
        return withFileAccess(
                lifecycle,
                remainingLockWait(deadline), false,
                (ignored, unused) -> {
                    FileChannel channel = FileChannel.open(
                            lifecycle, StandardOpenOption.WRITE, StandardOpenOption.CREATE, LinkOption.NOFOLLOW_LINKS);
                    try {
                        if (tryAcquireLock(channel, false, deadline) == null)
                            return false;

                        return operation.run(lifecycle, deadline);
                    } finally {
                        closeLockChannel(lifecycle, channel);
                    }
                });
    }

    private static boolean tryPublishFirst(Path staged, Path target, Path lifecycle) throws IOException {
        // All publishers enter the stable lifecycle lock before opening any legacy coordinator.
        // Try once and release the lifecycle lock before waiting on another file.
        if (usesLifecycleFilename(target))
            return tryPublishCurrent(staged, target);

        LockedFile legacy;
        try {
            legacy = openLockedFile(lifecycle.resolveSibling(target.getFileName()),
                    false, System.nanoTime(), StandardOpenOption.WRITE);
        } catch (NoSuchFileException e) {
            return tryPublishCurrent(staged, target);
        }
        if (legacy == null)
            return false;

        try {
            return tryPublishCurrent(staged, target);
        } finally {
            retireLegacyCreationLock(legacy);
        }
    }

    private static boolean tryPublishCurrent(Path staged, Path target) throws IOException {
        LockedFile current;
        try {
            current = openLockedFile(target, false, System.nanoTime(), StandardOpenOption.WRITE);
        } catch (NoSuchFileException e) {
            replaceAtomically(staged, target, false);
            return true;
        }
        if (current == null)
            return false;

        try (current) {
            replaceLocked(staged, current);
            return true;
        }
    }

    private static boolean usesLifecycleFilename(Path target) {
        return target.getFileName().toString().equalsIgnoreCase(CREATION_LIFECYCLE_LOCK);
    }

    private static void cleanLegacyCreationLock(Path target) {
        if (usesLifecycleFilename(target))
            return;

        Path lockDirectory = target.getParent().resolve(CREATION_LOCK_DIRECTORY);
        Path legacy = lockDirectory.resolve(target.getFileName());
        try {
            if (!Files.isRegularFile(legacy, LinkOption.NOFOLLOW_LINKS))
                return;

            withCreationLifecycle(lockDirectory.resolve(CREATION_LIFECYCLE_LOCK), System.nanoTime(), (path, ignored) -> {
                try {
                    LockedFile idle = openLockedFile(legacy, false, System.nanoTime(), StandardOpenOption.WRITE);
                    if (idle != null)
                        retireLegacyCreationLock(idle);
                } catch (NoSuchFileException gone) {
                    // Another publisher already removed the legacy file.
                }
                return true;
            });
        } catch (IOException | SecurityException | UnsupportedOperationException e) {
            log.warn("Could not clean creation lock [{}]", legacy, e);
        }
    }

    private static void retireLegacyCreationLock(LockedFile legacy) {
        try {
            boolean delete;
            try (FileChannel channel = legacy.channel()) {
                delete = channel.size() == 0L && legacy.isCurrent();
            }
            // The lifecycle lock still prevents another process from opening this path.
            if (delete)
                Files.deleteIfExists(legacy.path());
        } catch (IOException | SecurityException | UnsupportedOperationException e) {
            log.warn("Could not remove creation lock [{}]", legacy.path(), e);
        }
    }

    private static long remainingLockWait(long deadline) {
        return Math.max(0L, deadline - System.nanoTime());
    }

    private static void replaceLocked(Path staged, LockedFile target) throws IOException {
        if (!target.isCurrent())
            throw new FileSystemException(target.path().toString(), null, "Target changed while locked");

        replaceAtomically(staged, target.path(), true);
    }

    private static void replaceAtomically(Path staged, Path target, boolean preservePermissions) throws IOException {
        if (preservePermissions) {
            PosixFileAttributeView permissions = Files.getFileAttributeView(target, PosixFileAttributeView.class);
            if (permissions != null)
                Files.setPosixFilePermissions(staged, permissions.readAttributes().permissions());

            AclFileAttributeView acl = Files.getFileAttributeView(target, AclFileAttributeView.class);
            if (acl != null)
                Files.getFileAttributeView(staged, AclFileAttributeView.class).setAcl(acl.getAcl());
        }
        Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean checkWritableTarget(Path target) throws IOException {
        try {
            readRegularFileAttributes(target);
        } catch (NoSuchFileException e) {
            return false;
        }

        if (!Files.isWritable(target))
            throw new AccessDeniedException(target.toString());

        return true;
    }

    public static String read(String filePath) {
        return read(Paths.get(filePath));
    }

    public static String read(String filePath, boolean sharedLock, boolean awaitAcquire) {
        return read(Paths.get(filePath), sharedLock, awaitAcquire);
    }

    /**
     * Read a UTF-8 file.
     *
     * @return the file content, or null if the file cannot be read or decoded
     */
    public static String read(Path filePath) {
        return read(filePath, false, NO_FILE_LOCK);
    }

    /**
     * Read a UTF-8 file with a lock.
     *
     * @param sharedLock   whether to request a shared lock
     * @param awaitAcquire whether to wait up to 750 milliseconds to acquire the lock
     * @return the file content, or null if the file cannot be read or the lock is unavailable
     */
    public static String read(Path filePath, boolean sharedLock, boolean awaitAcquire) {
        return read(filePath, sharedLock, awaitAcquire ? READ_LOCK_TIMEOUT_MILLIS : 0L, TimeUnit.MILLISECONDS);
    }

    /**
     * Read a UTF-8 file with a lock.
     *
     * @param sharedLock         whether to request a shared lock
     * @param awaitAcquireOfTime the maximum lock wait, with a nonpositive value allowing one attempt
     * @param awaitTimeUnit      the unit of the lock wait
     * @return the file content, or null if the file cannot be read or the lock is unavailable
     */
    public static String read(Path filePath, boolean sharedLock, long awaitAcquireOfTime, TimeUnit awaitTimeUnit) {
        long timeoutNanos = awaitAcquireOfTime > 0L ? awaitTimeUnit.toNanos(awaitAcquireOfTime) : 0L;
        return read(filePath, sharedLock, timeoutNanos);
    }

    private static String read(Path filePath, boolean sharedLock, long lockTimeoutNanos) {
        log.debug("Reading file: [{}]", filePath.toAbsolutePath());
        boolean lockRequired = lockTimeoutNanos != NO_FILE_LOCK;

        try {
            return withFileAccess(filePath, lockTimeoutNanos, null, (target, deadline) -> {
                if (!lockRequired) {
                    if (readRegularFileAttributes(target).size() <= FileReaderUtils.SMALL_FILE_LIMIT)
                        return Files.readString(target, StandardCharsets.UTF_8);

                    try (FileChannel channel = FileChannel.open(target, StandardOpenOption.READ)) {
                        return FileReaderUtils.readBuffer(channel);
                    }
                }

                try (LockedFile locked = sharedLock
                        ? openLockedFile(target, true, deadline, StandardOpenOption.READ)
                        : openLockedFile(target, false, deadline, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    if (locked == null) {
                        log.debug("Could not acquire read lock for [{}]", target);
                        return null;
                    }
                    return FileReaderUtils.readBuffer(locked.channel());
                }
            });
        } catch (NoSuchFileException e) {
            log.warn("File does not exist: [{}]", filePath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to read file [{}]", filePath.toAbsolutePath(), e);
        }
        return null;
    }

    private static BasicFileAttributes readRegularFileAttributes(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile())
            throw new FileSystemException(path.toString(), null, "Expected a regular file");

        return attributes;
    }

    private static LockedFile openLockedFile(Path path, boolean shared, long deadline,
                                             StandardOpenOption... options) throws IOException {
        do {
            BasicFileAttributes attributes = readRegularFileAttributes(path);
            FileChannel channel = FileChannel.open(path, options);
            LockedFile opened = new LockedFile(path, channel, attributes);
            boolean keepOpen = false;
            try {
                if (tryAcquireLock(channel, shared, deadline) == null)
                    return null;

                // A wait can finish on a channel to a file that has since been replaced.
                if (opened.isCurrent()) {
                    keepOpen = true;
                    return opened;
                }
            } finally {
                if (!keepOpen)
                    opened.close();
            }
        } while (deadline - System.nanoTime() > 0L);

        return null;
    }

    private static FileLock tryAcquireLock(FileChannel channel, boolean shared, long deadline) throws IOException {
        do {
            if (Thread.currentThread().isInterrupted())
                return null;

            try {
                FileLock lock = channel.tryLock(0L, Long.MAX_VALUE, shared);
                if (lock != null)
                    return lock;

            } catch (OverlappingFileLockException ignored) {
                // A channel opened elsewhere in this JVM can hold an overlapping lock.
            }

        } while (waitForLockRetry(deadline));

        return null;
    }

    private static boolean waitForLockRetry(long deadline) {
        long remaining = remainingLockWait(deadline);
        if (remaining == 0L || Thread.currentThread().isInterrupted())
            return false;

        try {
            TimeUnit.NANOSECONDS.sleep(Math.min(remaining, LOCK_RETRY_INTERVAL_NANOS));
            return remainingLockWait(deadline) > 0L;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static <T> T withFileAccess(Path filePath,
                                        long lockTimeoutNanos,
                                        T unavailable,
                                        FileOperation<T> operation) throws IOException {

        long deadline = System.nanoTime() + Math.max(0L, lockTimeoutNanos);
        Path key = fileAccessKey(filePath.toAbsolutePath());

        LocalFileAccess access = FILE_ACCESS.compute(key, (path, existing) -> {
            LocalFileAccess entry = existing == null ? new LocalFileAccess() : existing;
            entry.users++;
            return entry;
        });

        // On some platforms, closing any channel on a file releases this JVM's native locks on it.
        // Coordinate opening and closing channels for the same resolved path inside this utility.
        try {
            if (access.lock.isHeldByCurrentThread()) {
                if (lockTimeoutNanos != NO_FILE_LOCK)
                    return unavailable;

                throw new IOException("Reentrant file access: " + filePath);
            }

            if (lockTimeoutNanos == NO_FILE_LOCK) {
                access.lock.lockInterruptibly();
            } else if (!access.lock.tryLock(remainingLockWait(deadline), TimeUnit.NANOSECONDS)) {
                return unavailable;
            }

            try {
                return operation.run(key, deadline);
            } finally {
                access.lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (lockTimeoutNanos != NO_FILE_LOCK)
                return unavailable;

            InterruptedIOException interrupted = new InterruptedIOException("Interrupted while accessing " + filePath);
            interrupted.initCause(e);
            throw interrupted;

        } finally {
            FILE_ACCESS.computeIfPresent(key, (path, entry) -> --entry.users == 0 ? null : entry);
        }
    }

    private static Path fileAccessKey(Path path) throws IOException {
        try {
            return path.toRealPath();
        } catch (NoSuchFileException e) {
            if (Files.isSymbolicLink(path))
                return fileAccessKey(path.resolveSibling(Files.readSymbolicLink(path)));

            Path parent = path.getParent();
            return parent == null ? path.normalize() : fileAccessKey(parent).resolve(path.getFileName()).normalize();
        }
    }

    public static List<Path> get(String directory) {
        return get(directory, null);
    }

    public static List<Path> get(String directory, Function<Path, Boolean> filter) {
        try (Stream<Path> streams = Files.list(Paths.get(directory))) {
            if (Objects.isNull(filter))
                return streams.toList();

            return streams.filter(filter::apply).toList();
        } catch (IOException e) {
            log.error("Read files is error, dir: {}, error: {}", directory, e.getMessage(), e);
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
            if (filter == null)
                streams.forEach(content);
            else
                streams.filter(filter::apply).forEach(content);
        } catch (IOException e) {
            log.error("Read files is error, dir: {}, error: {}", directory, e.getMessage(), e);
        }
    }

    public static BasicFileAttributes readAttributes(Path filePath) {
        try {
            return Files.readAttributes(filePath, BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            log.warn("File does not exist: [{}]", filePath.toAbsolutePath(), e);
        } catch (IOException e) {
            log.error("Failed to read file attributes [{}]", filePath.toAbsolutePath(), e);
        }
        return null;
    }

    private static long writtenString(FileChannel channel, String content) throws IOException {
        if (content.length() <= BUFFER_SIZE) {
            return writeFully(channel, ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
        }

        // 初始化编码组件
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        CharBuffer chars = CharBuffer.wrap(content);

        // Direct buffer reduces intermediate copying for file I/O
        ByteBuffer bytes = ByteBuffer.allocateDirect(BUFFER_SIZE);

        // 分块编码循环
        CoderResult result;
        long totalWritten = 0L;

        do {
            result = encoder.encode(chars, bytes, true);
            if (result.isError())
                result.throwException();

            totalWritten += flushBuffer(channel, bytes);
        } while (result.isOverflow());

        do {
            result = encoder.flush(bytes);
            if (result.isError())
                result.throwException();

            totalWritten += flushBuffer(channel, bytes);
        } while (result.isOverflow());

        return totalWritten;
    }

    private static int flushBuffer(FileChannel channel, ByteBuffer buffer) throws IOException {
        buffer.flip();
        int written = writeFully(channel, buffer);
        buffer.clear();
        return written;
    }

    private static int writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        int byteCount = buffer.remaining();
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        return byteCount;
    }

    private static long writtenStream(FileChannel channel, InputStream inputStream) throws IOException {
        byte[] bytes = new byte[BUFFER_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        long totalWritten = 0L;
        int bytesRead;

        while ((bytesRead = inputStream.read(bytes)) != -1) {
            buffer.clear();
            buffer.limit(bytesRead);
            totalWritten += writeFully(channel, buffer);
        }

        return totalWritten;
    }

    private static void closeLockChannel(Path path, FileChannel channel) {
        // Report cleanup separately because the atomic move may already have committed.
        try {
            channel.close();
        } catch (IOException e) {
            log.warn("Failed to close the lock channel for [{}]", path, e);
        }
    }

    private record LockedFile(Path path, FileChannel channel, BasicFileAttributes attributes)
            implements AutoCloseable {
        private boolean isCurrent() throws IOException {
            BasicFileAttributes current = readRegularFileAttributes(path);
            Object key = attributes.fileKey();
            Object currentKey = current.fileKey();
            if (key != null || currentKey != null)
                return Objects.equals(key, currentKey);

            // Some providers do not expose file keys. Check the available attributes for changes.
            return attributes.creationTime().equals(current.creationTime())
                    && attributes.lastModifiedTime().equals(current.lastModifiedTime())
                    && attributes.size() == current.size();
        }

        @Override
        public void close() {
            closeLockChannel(path, channel);
        }
    }

    private static final class LocalFileAccess {
        private final ReentrantLock lock = new ReentrantLock();
        private int users;
    }

    @FunctionalInterface
    private interface FileOperation<T> {
        T run(Path target, long deadline) throws IOException;
    }

    @FunctionalInterface
    private interface ChannelWriter {
        long write(FileChannel channel) throws IOException;
    }
}
