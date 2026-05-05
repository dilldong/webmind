package org.mind.framework.service;

/**
 * @author marcus
 */
public interface Cloneable<T> extends java.lang.Cloneable {
    T clone();

    enum CloneType {
        NONE, CLONE
    }
}
