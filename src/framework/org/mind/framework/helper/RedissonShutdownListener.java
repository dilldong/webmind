package org.mind.framework.helper;

import org.redisson.api.RedissonClient;

import java.util.function.Consumer;

public interface RedissonShutdownListener extends Consumer<RedissonClient> {

}
