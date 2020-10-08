package com.oracle.graal.python.builtins.modules;

import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.DirFdConversionNode;
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

public class DirFdConversionNodeTests {
    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        PythonTests.enterContext();
    }

    @Test
    public void none() {
        Assert.assertEquals(DirFdConversionNode.DEFAULT, call(PNone.NONE));
        Assert.assertEquals(DirFdConversionNode.DEFAULT, call(PNone.NO_VALUE));
    }

    @Test
    public void fdBool() {
        Assert.assertEquals(0, call(false));
        Assert.assertEquals(1, call(true));
    }

    @Test
    public void fdInt() {
        Assert.assertEquals(42, call(42));
    }

    @Test
    public void longFitsInt() {
        Assert.assertEquals(42, call(42L));
    }

    @Test
    public void longTooBig() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is greater than maximum");
        call(1L << 40);
    }

    @Test
    public void longTooSmall() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is less than minimum");
        call(-1L << 40);
    }

    @Test
    public void pintFitsInt() {
        Assert.assertEquals(42, call(factory().createInt(BigInteger.valueOf(42))));
    }

    @Test
    public void pintTooBig() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is greater than maximum");
        call(factory().createInt(BigInteger.ONE.shiftLeft(100)));
    }

    @Test
    public void pintTooSmall() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is less than minimum");
        call(factory().createInt(BigInteger.ONE.shiftLeft(100).negate()));
    }

    @Test
    public void indexFitsInt() {
        Assert.assertEquals(42, call(evalValue("class C:\n  def __index__(self):\n    return 42\nC()")));
    }

    @Test
    public void indexTooBig() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is greater than maximum");
        call(evalValue("class C:\n  def __index__(self):\n    return 1 << 40\nC()"));
    }

    @Test
    public void indexTooSmall() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("OverflowError: fd is less than minimum");
        call(evalValue("class C:\n  def __index__(self):\n    return -1 << 100\nC()"));
    }

    @Test
    public void indexInIntSubclass() {
        Assert.assertEquals(42, call(evalValue("class C(int):\n  def __index__(self):\n    return 123\nC(42)")));
    }

    @Test
    public void unsupportedType1() {
        expectedException.expect(PException.class);
        expectedException.expectMessage("TypeError: argument should be integer or None, not float");
        call(3.14);
    }

    private static int call(Object arg) {
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new RootNode(null) {
            @Child private DirFdConversionNode node = PosixModuleBuiltinsFactory.DirFdConversionNodeGen.create();

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
        Object result = callTarget.call();
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
