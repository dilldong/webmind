package org.mind.framework.exception;

/**
 * @author: Marcus
 * @date: 2026/10/14
 * @version: 1.0
 */
public class QueueFullException extends RuntimeException {
    public QueueFullException(String message) {
        super(message);
    }
}
