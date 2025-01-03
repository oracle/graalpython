package org.graalvm.python.embedding.tools.exec;

import java.util.ArrayList;
import java.util.List;

public interface BuildToolLog {
    default void subProcessOut(CharSequence out) {
        System.out.println(out);
    }

    default void subProcessErr(CharSequence err) {
        System.err.println(err);
    }

    default void info(String s) {
        System.out.println(s);
    };

    default void warning(String s) {
        System.out.println(s);
    }

    default void warning(String s, Throwable t) {
        System.out.println(s);
        t.printStackTrace();
    }

    final class CollectOutputLog implements BuildToolLog {
        private final List<String> output = new ArrayList<>();

        public List<String> getOutput() {
            return output;
        }

        @Override
        public void subProcessOut(CharSequence var1) {
            output.add(var1.toString());
        }

        @Override
        public void subProcessErr(CharSequence var1) {
            System.err.println(var1);
        }
    }
}
