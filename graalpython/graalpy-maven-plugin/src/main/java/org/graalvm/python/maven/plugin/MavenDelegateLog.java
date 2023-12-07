package org.graalvm.python.maven.plugin;

import org.apache.maven.plugin.logging.Log;
import org.graalvm.python.embedding.utils.SubprocessLog;

final class MavenDelegateLog implements SubprocessLog {
    private final Log delegate;

    MavenDelegateLog(Log delegate) {
        this.delegate = delegate;
    }

    public void subProcessOut(CharSequence var1) {
        System.out.println(var1.toString());
    }

    public void subProcessErr(CharSequence var1) {
        System.err.println(var1.toString());
    }

    public void log(CharSequence var1) {
        delegate.info(var1);
    }

    public void log(CharSequence var1, Throwable t) {
        delegate.error(var1, t);
    }
}
