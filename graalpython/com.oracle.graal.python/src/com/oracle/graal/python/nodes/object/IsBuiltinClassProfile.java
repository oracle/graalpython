/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.object;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;

public final class IsBuiltinClassProfile extends Node {
    @CompilationFinal private boolean isBuiltinType;
    @CompilationFinal private boolean isBuiltinClass;
    @CompilationFinal private boolean isOtherClass;

    @CompilationFinal private boolean match;
    @CompilationFinal private boolean noMatch;
    @CompilationFinal private boolean adoptable;

    @Child private PythonObjectLibrary lib;

    private static final IsBuiltinClassProfile UNCACHED = new IsBuiltinClassProfile(false);

    /* private constructor */
    private IsBuiltinClassProfile(boolean isCached) {
        if (isCached) {
            this.lib = PythonObjectLibrary.getFactory().createDispatched(3);
        } else {
            this.lib = PythonObjectLibrary.getUncached();
        }
        this.adoptable = isCached;
    }

    public static IsBuiltinClassProfile create() {
        return new IsBuiltinClassProfile(true);
    }

    public static IsBuiltinClassProfile getUncached() {
        return UNCACHED;
    }

    public boolean profileIsAnyBuiltinObject(PythonObject object) {
        return profileIsAnyBuiltinClass(lib.getLazyPythonClass(object));
    }

    public boolean profileIsOtherBuiltinObject(PythonObject object, PythonBuiltinClassType type) {
        return profileIsOtherBuiltinClass(lib.getLazyPythonClass(object), type);
    }

    public boolean profileException(PException object, PythonBuiltinClassType type) {
        return profileClass(lib.getLazyPythonClass(object.getExceptionObject()), type);
    }

    public boolean profileException(PException object, PythonBuiltinClassType type, PythonObjectLibrary elib) {
        return profileClass(elib.getLazyPythonClass(object.getExceptionObject()), type);
    }

    public boolean profileObject(Object object, PythonBuiltinClassType type) {
        return profileClass(lib.getLazyPythonClass(object), type);

    }

    public boolean profileIsAnyBuiltinClass(Object clazz) {
        if (clazz instanceof PythonBuiltinClassType) {
            if (!isBuiltinType) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isBuiltinType = true;
            }
            return true;
        } else {
            if (clazz instanceof PythonBuiltinClass) {
                if (!isBuiltinClass) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    isBuiltinClass = true;
                }
                return true;
            }
            if (!isOtherClass) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isOtherClass = true;
            }
            return false;
        }
    }

    public boolean profileIsOtherBuiltinClass(Object clazz, PythonBuiltinClassType type) {
        if (clazz instanceof PythonBuiltinClassType) {
            if (!isBuiltinType) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isBuiltinType = true;
            }
            if (clazz == type) {
                if (!match) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    match = true;
                }
                return false;
            } else {
                if (!noMatch) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    noMatch = true;
                }
                return true;
            }
        } else {
            if (clazz instanceof PythonBuiltinClass) {
                if (!isBuiltinClass) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    isBuiltinClass = true;
                }
                if (((PythonBuiltinClass) clazz).getType() == type) {
                    if (!match) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        match = true;
                    }
                    return false;
                } else {
                    if (!noMatch) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        noMatch = true;
                    }
                    return true;
                }
            }
            if (!isOtherClass) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isOtherClass = true;
            }
            return false;
        }
    }

    public boolean profileClass(Object clazz, PythonBuiltinClassType type) {
        if (clazz instanceof PythonBuiltinClassType) {
            if (!isBuiltinType) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isBuiltinType = true;
            }
            if (clazz == type) {
                if (!match) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    match = true;
                }
                return true;
            } else {
                if (!noMatch) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    noMatch = true;
                }
                return false;
            }
        } else {
            if (clazz instanceof PythonBuiltinClass) {
                if (!isBuiltinClass) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    isBuiltinClass = true;
                }
                if (((PythonBuiltinClass) clazz).getType() == type) {
                    if (!match) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        match = true;
                    }
                    return true;
                } else {
                    if (!noMatch) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        noMatch = true;
                    }
                    return false;
                }
            }
            if (!isOtherClass) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isOtherClass = true;
            }
            return false;
        }
    }

    public static boolean profileClassSlowPath(Object clazz, PythonBuiltinClassType type) {
        if (clazz instanceof PythonBuiltinClassType) {
            return clazz == type;
        } else {
            if (clazz instanceof PythonBuiltinClass) {
                return ((PythonBuiltinClass) clazz).getType() == type;
            }
            return false;
        }
    }

    @Override
    public boolean isAdoptable() {
        return adoptable;
    }
}
