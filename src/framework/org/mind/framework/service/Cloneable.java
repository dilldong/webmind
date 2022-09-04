package org.mind.framework.service;

public interface Cloneable<T> extends java.lang.Cloneable {
    T clone();

    enum CloneType {
        ORIGINAL, CLONE
    }
}
