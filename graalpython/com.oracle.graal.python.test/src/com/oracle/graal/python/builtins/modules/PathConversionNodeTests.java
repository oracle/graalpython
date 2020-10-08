/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PathConversionNode;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PosixPath;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.polyglot.Context;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.math.BigInteger;

import static com.oracle.graal.python.PythonLanguage.getContext;

public class PathConversionNodeTests {
    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        PythonTests.enterContext();
    }

    @Test
    public void noneAllowed() {
        Assert.assertEquals(PosixPath.DEFAULT, call(true, false, PNone.NONE));
        Assert.assertEquals(PosixPath.DEFAULT, call(true, false, PNone.NO_VALUE));
    }

    @Test
    public void noneForbiddenWithFd() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: fun: arg should be string, bytes, os.PathLike or integer, not NoneType");
        call(false, true, PNone.NONE);
    }

    @Test
    public void noValueForbiddenWithoutFd() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: fun: arg should be string, bytes or os.PathLike, not NoneType");
        call(false, false, PNone.NO_VALUE);
    }

    @Test
    public void string() {
        Assert.assertEquals("abc", callAndExpectPath(false, false, "abc"));
        Assert.assertEquals("abc", callAndExpectPath(false, false, factory().createString("abc")));
    }

    @Test
    public void stringWithZero() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("ValueError: fun: embedded null character in arg");
        call(false, false, "a\0c");
    }

    @Test
    public void bytes() {
        Assert.assertEquals("abc", callAndExpectPath(false, false, factory().createBytes("abc".getBytes())));
        Assert.assertEquals("abc", callAndExpectPath(false, true, factory().createBytes("abc".getBytes())));
        Assert.assertEquals("abc", callAndExpectPath(true, false, factory().createBytes("abc".getBytes())));
        Assert.assertEquals("abc", callAndExpectPath(true, true, factory().createBytes("abc".getBytes())));
    }

    @Test
    public void bytesWithZero() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("ValueError: fun: embedded null character in arg");
        call(false, false, factory().createBytes("a\0c".getBytes()));
    }

    @Test
    public void buffer() {
        Assert.assertEquals("abc", callAndExpectPath(false, false, evalValue("import array\narray.array('B', b'abc')")));
        // TODO: can we assert somehow that a warning is actually produced?
    }

    @Test
    public void bufferWithZero() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("ValueError: fun: embedded null character in arg");
        call(false, false, evalValue("import array\narray.array('B', b'a\\0c')"));
    }

    @Test
    public void boolAllowed() {
        Assert.assertEquals(0, callAndExpectFd(false));
        Assert.assertEquals(1, callAndExpectFd(true));
    }

    @Test
    public void boolForbidden() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: fun: arg should be string, bytes, os.PathLike or None, not bool");
        call(true, false, true);
    }

    @Test
    public void intAllowed() {
        Assert.assertEquals(42, callAndExpectFd(42));
    }

    @Test
    public void intForbiddenWithNullable() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: fun: arg should be string, bytes, os.PathLike or None, not int");
        call(true, false, 42);
    }

    @Test
    public void intForbiddenWithoutNullable() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: fun: arg should be string, bytes or os.PathLike, not int");
        call(false, false, 42);
    }

    @Test
    public void longFitsInt() {
        Assert.assertEquals(42, callAndExpectFd(42L));
    }

    @Test
    public void longTooBig() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is greater than maximum");
        call(false, true, 1L << 40);
    }

    @Test
    public void longTooSmall() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is less than minimum");
        call(false, true, -1L << 40);
    }

    @Test
    public void longForbidden() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: fun: arg should be string, bytes, os.PathLike or None, not int");
        call(true, false, 42L);
    }

    @Test
    public void pintFitsInt() {
        Assert.assertEquals(42, callAndExpectFd(factory().createInt(BigInteger.valueOf(42))));
    }

    @Test
    public void pintTooBig() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is greater than maximum");
        call(false, true, factory().createInt(BigInteger.ONE.shiftLeft(100)));
    }

    @Test
    public void pintTooSmall() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is less than minimum");
        call(false, true, factory().createInt(BigInteger.ONE.shiftLeft(100).negate()));
    }

    @Test
    public void pintForbidden() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: fun: arg should be string, bytes, os.PathLike or None, not int");
        call(true, false, factory().createInt(BigInteger.valueOf(42)));
    }

    @Test
    public void indexFitsInt() {
        Assert.assertEquals(42, callAndExpectFd(evalValue("class C:\n  def __index__(self):\n    return 42\nC()")));
    }

    @Test
    public void indexTooBig() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is greater than maximum");
        call(false, true, evalValue("class C:\n  def __index__(self):\n    return 1 << 40\nC()"));
    }

    @Test
    public void indexTooSmall() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is less than minimum");
        call(false, true, evalValue("class C:\n  def __index__(self):\n    return -1 << 100\nC()"));
    }

    @Test
    public void indexForbidden() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: fun: arg should be string, bytes, os.PathLike or None, not C");
        call(true, false, evalValue("class C:\n  def __index__(self):\n    return 42\nC()"));
    }

    @Test
    public void indexInIntSubclass() {
        Assert.assertEquals(42, callAndExpectFd(evalValue("class C(int):\n  def __index__(self):\n    return 123\nC(42)")));
    }

    @Test
    public void fspathBytes() {
        Assert.assertEquals("abc", callAndExpectPathEx("p = b'abc'\nclass C:\n  def __fspath__(self):\n    return p\n(C(), p)"));
    }

    @Test
    public void fspathString() {
        Assert.assertEquals("abc", callAndExpectPathEx("p = 'abc'\nclass C:\n  def __fspath__(self):\n    return p\n(C(), p)"));
    }

    @Test
    public void fspathPString() {
        Assert.assertEquals("abc", callAndExpectPathEx("class S(str):\n  pass\np = S('abc')\nclass C:\n  def __fspath__(self):\n    return p\n(C(), p)"));
    }

    @Test
    public void fspathNone() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: expected C.__fspath__() to return str or bytes, not NoneType");
        call(true, true, evalValue("class C:\n  def __fspath__(self):\n    return None\nC()"));
    }

    @Test
    public void fspathInt() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: expected C.__fspath__() to return str or bytes, not int");
        call(true, true, evalValue("class C:\n  def __fspath__(self):\n    return 42\nC()"));
    }

    @Test
    public void fspathFloat() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: expected C.__fspath__() to return str or bytes, not float");
        call(true, true, evalValue("class C:\n  def __fspath__(self):\n    return 3.14\nC()"));
    }

    @Test
    public void fspathBufferLike() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: expected C.__fspath__() to return str or bytes, not array");
        call(true, true, evalValue("class C:\n  def __fspath__(self):\n    import array\n    return array.array('B', b'abc')\nC()"));
    }

    @Test
    public void unsupportedType1() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: fun: arg should be string, bytes, os.PathLike, integer or None, not float");
        call(true, true, 3.14);
    }

    @Test
    public void unsupportedType2() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: fun: arg should be string, bytes, os.PathLike or None, not float");
        call(true, false, 3.14);
    }

    @Test
    public void unsupportedType3() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: fun: arg should be string, bytes, os.PathLike or integer, not float");
        call(false, true, 3.14);
    }

    @Test
    public void unsupportedType4() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: fun: arg should be string, bytes or os.PathLike, not float");
        call(false, false, 3.14);
    }

    private static Object call(boolean nullable, boolean allowFd, Object arg) {
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new RootNode(null) {
            @Child private PathConversionNode node = PosixModuleBuiltinsFactory.PathConversionNodeGen.create("fun", "arg", nullable, allowFd);

            @Override
            public Object execute(VirtualFrame frame) {
                Object[] arguments = PArguments.create(0);
                PythonContext pythonContext = getContext();
                PArguments.setGlobals(arguments, pythonContext.getCore().factory().createDict());
                PFrame.Reference frameInfo = ExecutionContext.IndirectCalleeContext.enterIndirect(pythonContext, arguments);
                PArguments.setCurrentFrameInfo(arguments, frameInfo);
                try {
                    return node.execute(Truffle.getRuntime().createMaterializedFrame(arguments), arg);
                } finally {
                    ExecutionContext.IndirectCalleeContext.exit(pythonContext, frameInfo);
                }
            }
        });
        return callTarget.call();
    }

    private static String callAndExpectPath(boolean nullable, boolean allowFd, Object arg, Object orig) {
        Object result = call(nullable, allowFd, arg);
        Assert.assertThat(result, CoreMatchers.instanceOf(PosixPath.Path.class));
        PosixPath.Path path = (PosixPath.Path) result;
        Assert.assertSame(orig, path.originalObject);
        return new String(path.path);
    }

    private static String callAndExpectPath(boolean nullable, boolean allowFd, Object arg) {
        return callAndExpectPath(nullable, allowFd, arg, arg);
    }

    private static String callAndExpectPathEx(String script) {
        PTuple o = (PTuple) evalValue(script);
        Object arg = o.getSequenceStorage().getItemNormalized(0);
        Object orig = o.getSequenceStorage().getItemNormalized(1);
        return callAndExpectPath(true, true, arg, orig);
    }

    private static int callAndExpectFd(Object arg) {
        Object result = call(true, true, arg);
        Assert.assertThat(result, CoreMatchers.instanceOf(PosixPath.Fd.class));
        PosixPath.Fd fd = (PosixPath.Fd) result;
        Assert.assertSame(arg, fd.originalObject);
        return fd.fd;
    }

    private static PythonObjectFactory factory() {
        return PythonObjectFactory.getUncached();
    }

    private static Object evalValue(String source) {
        return getContext().getEnv().asGuestValue(Context.getCurrent().eval("python", source));
    }
}
