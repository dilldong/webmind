package org.mind.framework;

import java.util.List;

public interface TestService {
    List<Object> get(String vars, long userId);

    String byCache(long userId);
}
