package org.mind.framework.annotation;

import java.io.Serializable;

@FunctionalInterface
public interface CacheinFace<T extends Serializable> {
    T getValue();
}
