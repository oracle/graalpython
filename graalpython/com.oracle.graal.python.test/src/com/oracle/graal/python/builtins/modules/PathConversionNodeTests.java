package com.oracle.graal.python.builtins.modules;

import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PathConversionNode;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PathT;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
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
        Assert.assertEquals(PathT.DEFAULT, callAndExpectPathT(true, false, PNone.NONE));
        Assert.assertEquals(PathT.DEFAULT, callAndExpectPathT(true, false, PNone.NO_VALUE));
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
        Assert.assertEquals("abc", new String(callAndExpectPathT(false, false, "abc").narrow));
        Assert.assertEquals("abc", new String(callAndExpectPathT(false, false, factory().createString("abc")).narrow));
    }

    @Test
    public void stringWithZero() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("ValueError: fun: embedded null character in arg");
        call(false, false, "a\0c");
    }

    @Test
    public void bytes() {
        Assert.assertEquals("abc", new String(callAndExpectPathT(false, false, factory().createBytes("abc".getBytes())).narrow));
        Assert.assertEquals("abc", new String(callAndExpectPathT(false, true, factory().createBytes("abc".getBytes())).narrow));
        Assert.assertEquals("abc", new String(callAndExpectPathT(true, false, factory().createBytes("abc".getBytes())).narrow));
        Assert.assertEquals("abc", new String(callAndExpectPathT(true, true, factory().createBytes("abc".getBytes())).narrow));
    }

    @Test
    public void bytesWithZero() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("ValueError: fun: embedded null character in arg");
        call(false, false, factory().createBytes("a\0c".getBytes()));
    }

    @Test
    public void buffer() {
        Assert.assertEquals("abc", new String(callAndExpectPathT(false, false, evalValue("import array\narray.array('B', b'abc')")).narrow));
        // TODO: can we assert somehow that a warning is actually produced?
    }

    @Test
    public void bufferWithZero() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("ValueError: fun: embedded null character in arg");
        call(false, false, evalValue("import array\narray.array('B', b'a\\0c')"));
    }

    @Test
    public void intAllowed() {
        Assert.assertEquals(42, callAndExpectInt(42));
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
        Assert.assertEquals(42, callAndExpectInt(42L));
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
        Assert.assertEquals(42, callAndExpectInt(factory().createInt(BigInteger.valueOf(42))));
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
        Assert.assertEquals(42, callAndExpectInt(evalValue("class C:\n  def __index__(self):\n    return 42\nC()")));
    }

    @Test
    public void indexTooBig() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is greater than maximum");
        call(false, true, evalValue("class C:\n  def __index__(self):\n    return 1 << 100\nC()"));
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
    public void fspathBytes() {
        Assert.assertEquals("abc", new String(callAndExpectPathT(true, true, evalValue("class C:\n  def __fspath__(self):\n    return b'abc'\nC()")).narrow));
    }

    @Test
    public void fspathString() {
        Assert.assertEquals("abc", new String(callAndExpectPathT(true, true, evalValue("class C:\n  def __fspath__(self):\n    return 'abc'\nC()")).narrow));
    }

    @Test
    public void fspathPString() {
        Assert.assertEquals("abc", new String(callAndExpectPathT(true, true, evalValue("class S(str):\n  pass\nclass C:\n  def __fspath__(self):\n    return S('abc')\nC()")).narrow));
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
                // TODO is this right? Without this, ReadCallerFrameNode (called by WarnNode) throws NPE
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

    private static PathT callAndExpectPathT(boolean nullable, boolean allowFd, Object arg) {
        Object result = call(nullable, allowFd, arg);
        Assert.assertThat(result, CoreMatchers.instanceOf(PathT.class));
        return (PathT) result;
    }

    private static int callAndExpectInt(Object arg) {
        Object result = call(true, true, arg);
        Assert.assertThat(result, CoreMatchers.instanceOf(Integer.class));
        return (int) result;
    }

    private static PythonObjectFactory factory() {
        return PythonObjectFactory.getUncached();
    }

    private static Object evalValue(String source) {
        return getContext().getEnv().asGuestValue(Context.getCurrent().eval("python", source));
    }
}
