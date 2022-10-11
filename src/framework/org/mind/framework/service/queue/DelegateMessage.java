package org.mind.framework.service.queue;

/**
 * 消息委托对象
 *
 * @author dp
 * @date Apr 25, 2012
 */
@FunctionalInterface
public interface DelegateMessage {
    void process();
}
