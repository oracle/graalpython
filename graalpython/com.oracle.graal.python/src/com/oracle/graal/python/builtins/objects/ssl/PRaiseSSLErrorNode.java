package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
public abstract class PRaiseSSLErrorNode extends Node {
    protected abstract PException execute(Node node, SSLErrorCode type, String message, Object[] args);

    public final PException raise(SSLErrorCode type, String message, Object... args) {
        return execute(this, type, message, args);
    }

    public static PException raiseUncached(Node node, SSLErrorCode type, String message, Object... args) {
        return PRaiseSSLErrorNodeGen.getUncached().execute(node, type, message, args);
    }

    @Specialization
    static PException raise(Node node, SSLErrorCode type, String message, Object[] args,
                    @CachedLanguage PythonLanguage language,
                    @Cached PythonObjectFactory factory,
                    @Cached WriteAttributeToObjectNode writeAttribute) {
        PBaseException exception = factory.createBaseException(type.getType(), message, args);
        writeAttribute.execute(exception, "errno", type.getErrno());
        return PRaiseNode.raise(node, exception, PythonOptions.isPExceptionWithJavaStacktrace(language));
    }
}
