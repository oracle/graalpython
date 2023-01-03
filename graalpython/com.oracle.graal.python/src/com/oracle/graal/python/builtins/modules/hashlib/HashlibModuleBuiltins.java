/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.modules.hashlib;

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.security.MessageDigest;
import java.security.Security;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObjectLibrary;

@CoreFunctions(defineModule = "_hashlib")
public class HashlibModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return HashlibModuleBuiltinsFactory.getFactories();
    }

    private static final String[] DIGEST_ALGORITHMS;
    static {
        var digests = new ArrayList<String>();
        for (var provider : Security.getProviders()) {
            for (var service : provider.getServices()) {
                if (service.getType().equalsIgnoreCase(MessageDigest.class.getSimpleName())) {
                    digests.add(service.getAlgorithm());
                }
            }
        }
        DIGEST_ALGORITHMS = digests.toArray(new String[digests.size()]);
    }

    @Override
    public void initialize(Python3Core core) {
        var algos = new LinkedHashMap<String, Object>();
        for (var digest : DIGEST_ALGORITHMS) {
            algos.put(digest, PNone.NONE);
        }
        addBuiltinConstant("openssl_md_meth_names", core.factory().createFrozenSet(EconomicMapStorage.create(algos)));

        // all our builtin hashes are cryptographically secure, alias them
        var dylib = DynamicObjectLibrary.getUncached();
        addDigestAlias(core, dylib, "md5", "_md5");
        addDigestAlias(core, dylib, "sha1", "_sha1");
        addDigestAlias(core, dylib, "sha224", "_sha256");
        addDigestAlias(core, dylib, "sha256", "_sha256");
        addDigestAlias(core, dylib, "sha384", "_sha512");
        addDigestAlias(core, dylib, "sha512", "_sha512");
        addDigestAlias(core, dylib, "sha3_sha224", "_sha3");
        addDigestAlias(core, dylib, "sha3_sha256", "_sha3");
        addDigestAlias(core, dylib, "sha3_sha384", "_sha3");
        addDigestAlias(core, dylib, "sha3_sha512", "_sha3");

        super.initialize(core);
    }

    private final void addDigestAlias(Python3Core core, DynamicObjectLibrary dylib, String digest, String module) {
        addBuiltinConstant("openssl_" + digest, dylib.getOrDefault(core.lookupBuiltinModule(toTruffleStringUncached(module)).getStorage(), toTruffleStringUncached(digest), PNone.NO_VALUE));
    }

    @Builtin(name = "HASH", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.HashlibHash, isPublic = false)
    @GenerateNodeFactory
    abstract static class HashNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object hash(Object args, Object kwargs) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "_hashlib.HASH");
        }
    }

    @Builtin(name = "HASHXOF", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.HashlibHash, isPublic = false)
    @GenerateNodeFactory
    abstract static class HashxofNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object hash(Object args, Object kwargs) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "_hashlib.HASH");
        }
    }

    @Builtin(name = "HMAC", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.HashlibHash, isPublic = false)
    @GenerateNodeFactory
    abstract static class HmacNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object hash(Object args, Object kwargs) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "_hashlib.HASH");
        }
    }
}
