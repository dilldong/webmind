package org.mind.framework.annotation;

import java.io.Serializable;

@FunctionalInterface
public interface EnumFace<T extends Serializable> {
    T getValue();
}
