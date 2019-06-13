/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.object.Shape;

public final class IsBuiltinClassProfile {
    @CompilationFinal private boolean isBuiltinType;
    @CompilationFinal private boolean isBuiltinClass;
    @CompilationFinal private boolean isOtherClass;

    @CompilationFinal private boolean match;
    @CompilationFinal private boolean noMatch;

    // n.b.: (tfel) We store the python class as a Shape property on the
    // DynamicObject representing the Python-level object. Thus, accessing the
    // python class incurs an indirection that we'd like to avoid if
    // possible. We use this cache to avoid the indirection. In the single
    // context case, we just cache all classes, in the multi-context case, we
    // only cache classes if they are builtin types that are shared across
    // contexts.
    private final Assumption singleContextAssumption = PythonLanguage.getCurrent().singleContextAssumption;
    private static final int CLASS_CACHE_SIZE = 3;
    @CompilationFinal(dimensions = 1) private ClassCache[] classCache = new ClassCache[CLASS_CACHE_SIZE];
    @CompilationFinal private boolean cacheUsedInSingleContext = false;

    private static final class ClassCache {
        private final LazyPythonClass klass;
        private final Shape shape;

        ClassCache(Shape shape, LazyPythonClass klass) {
            this.shape = shape;
            this.klass = klass;
        }
    }

    private static final IsBuiltinClassProfile UNCACHED = new IsBuiltinClassProfile();
    static {
        UNCACHED.classCache = null;
    }

    private IsBuiltinClassProfile() {
        // private constructor
    }

    public static IsBuiltinClassProfile create() {
        return new IsBuiltinClassProfile();
    }

    public static IsBuiltinClassProfile getUncached() {
        return UNCACHED;
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private LazyPythonClass getLazyPythonClass(PythonObject object) {
        if (classCache != null) {
            // we're still caching
            if (!singleContextAssumption.isValid() && cacheUsedInSingleContext) {
                // we previously used this cache in a single context, now we're
                // in a multi-context mode. Reset the cache.
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cacheUsedInSingleContext = false;
                classCache = new ClassCache[CLASS_CACHE_SIZE];
            }
            for (int i = 0; i < classCache.length; i++) {
                ClassCache cache = classCache[i];
                if (cache == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Shape shape = object.getStorage().getShape();
                    LazyPythonClass klass = PythonObject.getLazyPythonClass(shape.getObjectType());
                    if (klass instanceof PythonBuiltinClassType) {
                        classCache[i] = new ClassCache(shape, klass);
                    } else if (singleContextAssumption.isValid()) {
                        // we're caching a non-builtin type, so if we switch to
                        // a multi-context, the cache needs to be flushed.
                        cacheUsedInSingleContext = true;
                        classCache[i] = new ClassCache(shape, klass);
                    } else {
                        classCache = null;
                    }
                    return klass;
                } else if (cache.shape == object.getStorage().getShape()) {
                    return cache.klass;
                }
            }
        }
        if (classCache != null) {
            // cache overflow, revert to generic access
            CompilerDirectives.transferToInterpreterAndInvalidate();
            classCache = null;
        }
        return object.getLazyPythonClass();
    }

    public boolean profileIsAnyBuiltinObject(PythonObject object) {
        return profileIsAnyBuiltinClass(getLazyPythonClass(object));
    }

    public boolean profileIsOtherBuiltinObject(PythonObject object, PythonBuiltinClassType type) {
        return profileIsOtherBuiltinClass(getLazyPythonClass(object), type);
    }

    public boolean profileException(PException object, PythonBuiltinClassType type) {
        return profileClass(getLazyPythonClass(object.getExceptionObject()), type);
    }

    public boolean profileObject(PythonObject object, PythonBuiltinClassType type) {
        return profileClass(getLazyPythonClass(object), type);

    }

    public boolean profileIsAnyBuiltinClass(LazyPythonClass clazz) {
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

    public boolean profileIsOtherBuiltinClass(LazyPythonClass clazz, PythonBuiltinClassType type) {
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

    public boolean profileClass(LazyPythonClass clazz, PythonBuiltinClassType type) {
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

    public boolean profileClass(PythonAbstractClass clazz, PythonBuiltinClassType type) {
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

    public static boolean profileClassSlowPath(LazyPythonClass clazz, PythonBuiltinClassType type) {
        if (clazz instanceof PythonBuiltinClassType) {
            return clazz == type;
        } else {
            if (clazz instanceof PythonBuiltinClass) {
                return ((PythonBuiltinClass) clazz).getType() == type;
            }
            return false;
        }
    }
}
