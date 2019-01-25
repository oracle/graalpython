/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PMMap;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.nio.channels.Channel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.SeekableByteChannel;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(defineModule = "mmap")
public class MMapModuleBuiltins extends PythonBuiltins {
    private static final int ACCESS_DEFAULT = 0;
    private static final int ACCESS_READ = 1;
    private static final int ACCESS_WRITE = 2;
    private static final int ACCESS_COPY = 3;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MMapModuleBuiltinsFactory.getFactories();
    }

    public MMapModuleBuiltins() {
        builtinConstants.put("ACCESS_DEFAULT", ACCESS_DEFAULT);
        builtinConstants.put("ACCESS_READ", ACCESS_READ);
        builtinConstants.put("ACCESS_WRITE", ACCESS_WRITE);
        builtinConstants.put("ACCESS_COPY", ACCESS_COPY);
    }

    @Builtin(name = "mmap", fixedNumOfPositionalArgs = 3, keywordArguments = {"tagname", "access"}, constructsClass = PMMap)
    @GenerateNodeFactory
    public abstract static class MMapNode extends PythonBuiltinNode {

        private final ValueProfile classProfile = ValueProfile.createClassProfile();

        @Specialization(guards = {"isNoValue(access)"})
        PMMap doIt(LazyPythonClass clazz, int fd, int length, Object tagname, @SuppressWarnings("unused") PNone access) {
            return doGeneric(clazz, fd, length, tagname, ACCESS_DEFAULT);
        }

        // mmap(fileno, length, tagname=None, access=ACCESS_DEFAULT[, offset])
        @Specialization
        PMMap doGeneric(LazyPythonClass clazz, int fd, int length, @SuppressWarnings("unused") Object tagname, int access) {
            Channel fileChannel = getContext().getResources().getFileChannel(fd, classProfile);
            if (fileChannel instanceof SeekableByteChannel) {
                MapMode mode = convertAccessToMapMode(access);
                return factory().createMMap(clazz, (SeekableByteChannel) fileChannel);
            }
            throw raise(ValueError, "cannot mmap file");
        }

        private MapMode convertAccessToMapMode(int access) {
            switch (access) {
                case 0:
                    return MapMode.READ_WRITE;
                case 1:
                    return MapMode.READ_ONLY;
                case 2:
                    return MapMode.READ_WRITE;
                case 3:
                    return MapMode.PRIVATE;
            }
            throw raise(ValueError, "mmap invalid access parameter.");
        }

    }
}
