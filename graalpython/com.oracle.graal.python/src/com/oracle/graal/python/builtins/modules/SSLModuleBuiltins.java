package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "_ssl")
public class SSLModuleBuiltins extends PythonBuiltins {
    // Taken from CPython
    static final String DEFAULT_CIPHERS = "DEFAULT:!aNULL:!eNULL:!MD5:!3DES:!DES:!RC4:!IDEA:!SEED:!aDSS:!SRP:!PSK";

    public static final int SSL_CERT_NONE = 0;
    public static final int SSL_CERT_OPTIONAL = 1;
    public static final int SSL_CERT_REQUIRED = 2;

    public static final int PROTO_MINIMUM_SUPPORTED = -2;
    public static final int PROTO_MAXIMUM_SUPPORTED = -1;
    public static final int PROTO_SSLv3 = 0x0300;
    public static final int PROTO_TLSv1 = 0x0301;
    public static final int PROTO_TLSv1_1 = 0x0302;
    public static final int PROTO_TLSv1_2 = 0x0303;
    public static final int PROTO_TLSv1_3 = 0x0304;

    public static final int SSL_VERSION_SSL2 = 0;
    public static final int SSL_VERSION_SSL3 = 1;
    public static final int SSL_VERSION_TLS = 2;
    public static final int SSL_VERSION_TLS1 = 3;
    public static final int SSL_VERSION_TLS1_1 = 4;
    public static final int SSL_VERSION_TLS1_2 = 5;
    public static final int SSL_VERSION_TLS_CLIENT = 0x10;
    public static final int SSL_VERSION_TLS_SERVER = 0x11;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SSLModuleBuiltinsFactory.getFactories();
    }

    @Override
    // TODO use initialize instead?
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        PythonModule module = core.lookupBuiltinModule("_ssl");
        PythonObjectFactory factory = PythonObjectFactory.getUncached();
        // TODO decide which values to pick
        module.setAttribute("OPENSSL_VERSION_NUMBER", 269488287);
        module.setAttribute("OPENSSL_VERSION_INFO", factory.createTuple(new int[]{1, 1, 1, 9, 15}));
        module.setAttribute("OPENSSL_VERSION", "Java");
        module.setAttribute("_DEFAULT_CIPHERS", DEFAULT_CIPHERS);
        module.setAttribute("_OPENSSL_API_VERSION", PNone.NONE);

        module.setAttribute("CERT_NONE", SSL_CERT_NONE);
        module.setAttribute("CERT_OPTIONAL", SSL_CERT_OPTIONAL);
        module.setAttribute("CERT_REQUIRED", SSL_CERT_REQUIRED);
        // TODO enable
        module.setAttribute("HAS_SNI", false);
        module.setAttribute("HAS_ECDH", false);
        module.setAttribute("HAS_NPN", false);
        module.setAttribute("HAS_ALPN", false);
        module.setAttribute("HAS_SSLv2", false);
        module.setAttribute("HAS_SSLv3", false);
        module.setAttribute("HAS_TLSv1", false);
        module.setAttribute("HAS_TLSv1_1", false);
        module.setAttribute("HAS_TLSv1_2", false);
        module.setAttribute("HAS_TLSv1_3", false);

        module.setAttribute("PROTO_MINIMUM_SUPPORTED", PROTO_MINIMUM_SUPPORTED);
        module.setAttribute("PROTO_MAXIMUM_SUPPORTED", PROTO_MAXIMUM_SUPPORTED);
        module.setAttribute("PROTO_SSLv3", PROTO_SSLv3);
        module.setAttribute("PROTO_TLSv1", PROTO_TLSv1);
        module.setAttribute("PROTO_TLSv1_1", PROTO_TLSv1_1);
        module.setAttribute("PROTO_TLSv1_2", PROTO_TLSv1_2);
        module.setAttribute("PROTO_TLSv1_3", PROTO_TLSv1_3);

        module.setAttribute("PROTOCOL_SSLv2", SSL_VERSION_SSL2);
        module.setAttribute("PROTOCOL_SSLv3", SSL_VERSION_SSL3);
        module.setAttribute("PROTOCOL_SSLv23", SSL_VERSION_TLS);
        module.setAttribute("PROTOCOL_TLS", SSL_VERSION_TLS);
        module.setAttribute("PROTOCOL_TLS_CLIENT", SSL_VERSION_TLS_CLIENT);
        module.setAttribute("PROTOCOL_TLS_SERVER", SSL_VERSION_TLS_SERVER);
        module.setAttribute("PROTOCOL_TLSv1", SSL_VERSION_TLS1);
        module.setAttribute("PROTOCOL_TLSv1_1", SSL_VERSION_TLS1_1);
        module.setAttribute("PROTOCOL_TLSv1_2", SSL_VERSION_TLS1_2);
    }

    @Builtin(name = "txt2obj", minNumOfPositionalArgs = 1, parameterNames = {"txt", "name"})
    @ArgumentClinic(name = "txt", conversion = ArgumentClinic.ClinicConversion.String)
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class Txt2ObjNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object txt2obj(String txt, boolean name) {
            // TODO implement properly
            if ("1.3.6.1.5.5.7.3.1".equals(txt)) {
                return factory().createTuple(new Object[]{129, "serverAuth", "TLS Web Server Authentication", txt});
            } else if ("1.3.6.1.5.5.7.3.2".equals(txt)) {
                return factory().createTuple(new Object[]{130, "clientAuth", "TLS Web Client Authentication", txt});
            }
            throw raise(NotImplementedError);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLModuleBuiltinsClinicProviders.Txt2ObjNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "nid2obj", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"nid"})
    @GenerateNodeFactory
    abstract static class Nid2ObjNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object nid2obj(Object nid) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "RAND_status")
    @GenerateNodeFactory
    abstract static class RandStatusNode extends PythonBuiltinNode {
        @Specialization
        Object randStatus() {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "RAND_add", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"string", "entropy"})
    @GenerateNodeFactory
    abstract static class RandAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object randAdd(Object string, Object entropy) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "RAND_bytes", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"n"})
    @GenerateNodeFactory
    abstract static class RandBytesNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object randBytes(Object n) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "RAND_pseudo_bytes", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"n"})
    @GenerateNodeFactory
    abstract static class RandPseudoBytesNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object randPseudoBytes(Object n) {
            throw raise(NotImplementedError);
        }
    }
}
