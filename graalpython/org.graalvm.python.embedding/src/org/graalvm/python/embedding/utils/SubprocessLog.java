package org.graalvm.python.embedding.utils;

public interface SubprocessLog {
    void subProcessOut(CharSequence var1);

    void subProcessErr(CharSequence var1);

    void log(CharSequence var1);

    void log(CharSequence var1, Throwable t);
}
