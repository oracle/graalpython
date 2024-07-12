package org.graalvm.python;

import groovy.util.logging.Log;
import org.graalvm.python.embedding.tools.exec.SubprocessLog;
import org.gradle.api.logging.Logger;

public class GradleLogger implements SubprocessLog {
    private Logger logger;

    private GradleLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void subProcessOut(CharSequence out) {
        logger.lifecycle(out.toString());
    }

    @Override
    public void subProcessErr(CharSequence err) {
        logger.warn(err.toString());
    }

    @Override
    public void log(CharSequence txt) {
        logger.lifecycle(txt.toString());
    }

    @Override
    public void log(CharSequence txt, Throwable t) {
        logger.lifecycle(txt.toString(), t);
    }

    public static GradleLogger of(Logger logger) {
        return new GradleLogger(logger);
    }
}
