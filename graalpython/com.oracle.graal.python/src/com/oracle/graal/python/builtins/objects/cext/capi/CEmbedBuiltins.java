package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.polyglot.Context;

public class CEmbedBuiltins {

    static Context pyContext;

    @CEntryPoint(name = "graalpy_init_embed")
    public static void GraalPyInitEmbed(IsolateThread thread) {
        System.out.println("GraalPyInitEmbed from thread " + thread.rawValue());
        pyContext = Context.newBuilder("python")
                .allowAllAccess(true)
                .option("python.PythonHome", "/Users/mkind/Dev/Uni/RE23/graal/sdk/mxbuild/darwin-aarch64/PYTHON_NATIVE_STANDALONE_SVM_JAVA21/graalpy-community-24.0.0-dev-macos-aarch64")
                .build();
        pyContext.initialize("python");
        pyContext.enter();
        GilNode.getUncached().acquire();
        CApiContext ctx = CApiContext.ensureCapiWasLoaded();
        System.out.println("GraalPyInitEmbed from thread " + thread.rawValue() + " done, got ctx " + ctx.toString());
        // get GIL

    }
}
