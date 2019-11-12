package com.oracle.graal.python.builtins.objects.bytes;

import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;

@GenerateLibrary
@DefaultExport(DefaultBufferStringExports.class)
@SuppressWarnings("unused")
public abstract class PythonBufferLibrary extends Library {

    @Abstract(ifExported = {"getBufferBytes", "getBufferLength"})
    public boolean isBuffer(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "getBufferBytes")
    public int getBufferLength(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = "getBufferLength")
    public byte[] getBufferBytes(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }
}
