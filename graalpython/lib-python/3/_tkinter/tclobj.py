# TclObject, conversions with Python objects

from .tklib_cffi import ffi as tkffi, lib as tklib
import binascii

class TypeCache(object):
    def __init__(self):
        self.OldBooleanType = tklib.Tcl_GetObjType(b"boolean")
        self.BooleanType = None
        self.ByteArrayType = tklib.Tcl_GetObjType(b"bytearray")
        self.DoubleType = tklib.Tcl_GetObjType(b"double")
        self.IntType = tklib.Tcl_GetObjType(b"int")
        self.WideIntType = tklib.Tcl_GetObjType(b"wideInt")
        self.BigNumType = None
        self.ListType = tklib.Tcl_GetObjType(b"list")
        self.ProcBodyType = tklib.Tcl_GetObjType(b"procbody")
        self.StringType = tklib.Tcl_GetObjType(b"string")

    def add_extra_types(self, app):
        # Some types are not registered in Tcl.
        result = app.call('expr', 'true')
        typePtr = AsObj(result).typePtr
        if FromTclString(tkffi.string(typePtr.name)) == "booleanString":
            self.BooleanType = typePtr

        result = app.call('expr', '2**63')
        typePtr = AsObj(result).typePtr
        if FromTclString(tkffi.string(typePtr.name)) == "bignum":
            self.BigNumType = typePtr


# Interprets a TCL string (untyped char array) as a Python str using UTF-8.
# This assumes that TCL encodes its return values as UTF-8, not UTF-16.
# TODO: Find out whether this assumption is correct.
def FromTclString(s: bytes) -> str:
    try:
        return s.decode("utf-8")
    except UnicodeDecodeError:
        # Tcl encodes null character as \xc0\x80
        return s.replace(b'\xc0\x80', b'\x00')\
                .decode('utf-8')

# Encodes a Python str as UTF-8 (assuming TCL encodes its API strings as UTF-8 as well, not UTF-16).
# TODO: Find out whether this is correct.
def ToTCLString(s: str) -> bytes:
    return s.encode("utf-8")\
            .replace(b"\x00", b"\xc0\x80")


# Only when tklib.HAVE_WIDE_INT_TYPE.
def FromWideIntObj(app, value):
    wide = tkffi.new("Tcl_WideInt*")
    if tklib.Tcl_GetWideIntFromObj(app.interp, value, wide) != tklib.TCL_OK:
        app.raiseTclError()
    return int(wide[0])

# Only when tklib.HAVE_LIBTOMMATH!
def FromBignumObj(app, value):
    bigValue = tkffi.new("mp_int*")
    if tklib.Tcl_GetBignumFromObj(app.interp, value, bigValue) != tklib.TCL_OK:
        app.raiseTclError()
    try:
        numBytes = tklib.mp_unsigned_bin_size(bigValue)
        buf = tkffi.new("unsigned char[]", numBytes)
        bufSize_ptr = tkffi.new("unsigned long*", numBytes)
        if tklib.mp_to_unsigned_bin_n(
                bigValue, buf, bufSize_ptr) != tklib.MP_OKAY:
            raise MemoryError
        if bufSize_ptr[0] == 0:
            return 0
        bytes = tkffi.buffer(buf)[0:bufSize_ptr[0]]
        sign = -1 if bigValue.sign == tklib.MP_NEG else 1
        return int(sign * int(binascii.hexlify(bytes), 16))
    finally:
        tklib.mp_clear(bigValue)

def AsBignumObj(value):
    sign = -1 if value < 0 else 1
    hexstr = '%x' % abs(value)
    bigValue = tkffi.new("mp_int*")
    tklib.mp_init(bigValue)
    try:
        if tklib.mp_read_radix(bigValue, hexstr, 16) != tklib.MP_OKAY:
            raise MemoryError
        bigValue.sign = tklib.MP_NEG if value < 0 else tklib.MP_ZPOS
        return tklib.Tcl_NewBignumObj(bigValue)
    finally:
        tklib.mp_clear(bigValue)


def FromObj(app, value):
    """Convert a TclObj pointer into a Python object."""
    typeCache = app._typeCache
    if not value.typePtr:
        buf = tkffi.buffer(value.bytes, value.length)
        return FromTclString(buf[:])

    if value.typePtr in (typeCache.BooleanType, typeCache.OldBooleanType):
        value_ptr = tkffi.new("int*")
        if tklib.Tcl_GetBooleanFromObj(
                app.interp, value, value_ptr) == tklib.TCL_ERROR:
            app.raiseTclError()
        return bool(value_ptr[0])
    if value.typePtr == typeCache.ByteArrayType:
        size = tkffi.new('int*')
        data = tklib.Tcl_GetByteArrayFromObj(value, size)
        return tkffi.buffer(data, size[0])[:]
    if value.typePtr == typeCache.DoubleType:
        return value.internalRep.doubleValue
    if value.typePtr == typeCache.IntType:
        return value.internalRep.longValue
    if value.typePtr == typeCache.WideIntType:
        return FromWideIntObj(app, value)
    if value.typePtr == typeCache.BigNumType and tklib.HAVE_LIBTOMMATH:
        return FromBignumObj(app, value)
    if value.typePtr == typeCache.ListType:
        size = tkffi.new('int*')
        status = tklib.Tcl_ListObjLength(app.interp, value, size)
        if status == tklib.TCL_ERROR:
            app.raiseTclError()
        result = []
        tcl_elem = tkffi.new("Tcl_Obj**")
        for i in range(size[0]):
            status = tklib.Tcl_ListObjIndex(app.interp,
                                            value, i, tcl_elem)
            if status == tklib.TCL_ERROR:
                app.raiseTclError()
            result.append(FromObj(app, tcl_elem[0]))
        return tuple(result)
    if value.typePtr == typeCache.ProcBodyType:
        pass  # fall through and return tcl object.
    if value.typePtr == typeCache.StringType:
        buf = tklib.Tcl_GetUnicode(value)
        length = tklib.Tcl_GetCharLength(value)
        buf = tkffi.buffer(tkffi.cast("char*", buf), length*2)[:]
        return buf.decode('utf-16')

    return TclObject(value)

def AsObj(value):
    if isinstance(value, str):
        # TCL uses UTF-16 internally (https://www.tcl.tk/man/tcl8.4/TclCmd/encoding.html)
        # But this function takes UTF-8 (https://linux.die.net/man/3/tcl_newstringobj#:~:text=array%20of%20UTF%2D8%2Dencoded%20bytes)
        return tklib.Tcl_NewStringObj(ToTCLString(value), len(value))
    if isinstance(value, bool):
        return tklib.Tcl_NewBooleanObj(value)
    if isinstance(value, int):
        try:
            return tklib.Tcl_NewLongObj(value)
        except OverflowError:
            # 64-bit windows
            if tklib.HAVE_WIDE_INT_TYPE:
                return tklib.Tcl_NewWideIntObj(value)
            else:
                import sys
                t, v, tb = sys.exc_info()
                raise t(v).with_traceback(tb)
    if isinstance(value, int):
        try:
            tkffi.new("long[]", [value])
        except OverflowError:
            pass 
        else:
            return tklib.Tcl_NewLongObj(value)
        if tklib.HAVE_WIDE_INT_TYPE:
            try:
                tkffi.new("Tcl_WideInt[]", [value])
            except OverflowError:
                pass
            else:
                return tklib.Tcl_NewWideIntObj(value)
        if tklib.HAVE_LIBTOMMATH:
            return AsBignumObj(value)
            
    if isinstance(value, float):
        return tklib.Tcl_NewDoubleObj(value)
    if isinstance(value, tuple):
        argv = tkffi.new("Tcl_Obj*[]", len(value))
        for i in range(len(value)):
            argv[i] = AsObj(value[i])
        return tklib.Tcl_NewListObj(len(value), argv)
    if isinstance(value, str):
        # TODO: Remnant of Python2's unicode type. What happens when our string contains unicode characters?
        # Should we encode it as UTF-8 or UTF-16?
        raise NotImplementedError
        encoded = value.encode('utf-16')[2:]
        buf = tkffi.new("char[]", encoded)
        inbuf = tkffi.cast("Tcl_UniChar*", buf)
        return tklib.Tcl_NewUnicodeObj(inbuf, len(encoded)/2)
    if isinstance(value, TclObject):
        return value._value

    return AsObj(str(value))

class TclObject(object):
    def __new__(cls, value):
        self = object.__new__(cls)
        tklib.Tcl_IncrRefCount(value)
        self._value = value
        self._string = None
        return self

    def __del__(self):
        tklib.Tcl_DecrRefCount(self._value)

    def __str__(self):
        if self._string and isinstance(self._string, str):
            return self._string
        return FromTclString(tkffi.string(tklib.Tcl_GetString(self._value)))

    def __repr__(self):
        return "<%s object at 0x%x>" % (
            self.typename, tkffi.cast("intptr_t", self._value))

    def __eq__(self, other):
        if not isinstance(other, TclObject):
            return NotImplemented
        return self._value == other._value

    @property
    def typename(self):
        return FromTclString(tkffi.string(self._value.typePtr.name))

    @property
    def string(self):
        if self._string is None:
            length = tkffi.new("int*")
            s = tklib.Tcl_GetStringFromObj(self._value, length)
            value = tkffi.buffer(s, length[0])[:]
            try:
                value.decode('ascii')
            except UnicodeDecodeError:
                value = value.decode('utf8')
            self._string = value
        return self._string
    