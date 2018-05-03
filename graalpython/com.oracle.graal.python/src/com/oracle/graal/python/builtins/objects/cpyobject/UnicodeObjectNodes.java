package com.oracle.graal.python.builtins.objects.cpyobject;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cpyobject.UnicodeObjectNodesFactory.UnicodeAsWideCharNodeGen;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class UnicodeObjectNodes {

    abstract static class UnicodeBaseNode extends PBaseNode {
        private static final int NATIVE_ORDER = 0;
        private static Charset UTF32;
        private static Charset UTF32LE;
        private static Charset UTF32BE;

        protected static Charset getUTF32Charset(int byteorder) {
            String utf32Name = getUTF32Name(byteorder);
            if (byteorder == UnicodeBaseNode.NATIVE_ORDER) {
                if (UTF32 == null) {
                    UTF32 = Charset.forName(utf32Name);
                }
                return UTF32;
            } else if (byteorder < UnicodeBaseNode.NATIVE_ORDER) {
                if (UTF32LE == null) {
                    UTF32LE = Charset.forName(utf32Name);
                }
                return UTF32LE;
            }
            if (UTF32BE == null) {
                UTF32BE = Charset.forName(utf32Name);
            }
            return UTF32BE;
        }

        protected static String getUTF32Name(int byteorder) {
            String csName;
            if (byteorder == 0) {
                csName = "UTF-32";
            } else if (byteorder < 0) {
                csName = "UTF-32LE";
            } else {
                csName = "UTF-32BE";
            }
            return csName;
        }
    }

    public abstract static class UnicodeAsWideCharNode extends UnicodeBaseNode {

        public abstract PBytes execute(Object obj, long elementSize, long elements);

        @Specialization
        PBytes doUnicode(PString s, long elementSize, long elements) {
            return doUnicode(s.getValue(), elementSize, elements);
        }

        @Specialization
        @TruffleBoundary
        PBytes doUnicode(String s, long elementSize, long elements) {
            // use native byte order
            Charset utf32Charset = getUTF32Charset(0);

            // elementSize == 2: Store String in 'wchar_t' of size == 2, i.e., use UCS2. This is
            // achieved by decoding to UTF32 (which is basically UCS4) and ignoring the two
            // MSBs.
            if (elementSize == 2L) {
                ByteBuffer bytes = ByteBuffer.wrap(s.getBytes(utf32Charset));
                // FIXME unsafe narrowing
                ByteBuffer buf = ByteBuffer.allocate(Math.min(bytes.remaining() / 2, (int) (elements * elementSize)));
                while (bytes.remaining() >= 4) {
                    buf.putChar((char) (bytes.getInt() & 0x0000FFFF));
                }
                buf.flip();
                byte[] barr = new byte[buf.remaining()];
                buf.get(barr);
                return factory().createBytes(barr);
            } else if (elementSize == 4L) {
                return factory().createBytes(s.getBytes(utf32Charset));
            } else {
                throw new RuntimeException("unsupported wchar size; was: " + elementSize);
            }
        }

        public static UnicodeAsWideCharNode create() {
            return UnicodeAsWideCharNodeGen.create();
        }
    }

}
