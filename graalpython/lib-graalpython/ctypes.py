class c_bool(bool):
    @staticmethod
    def size():
        return 1


class c_char(int):
    @staticmethod
    def size():
        return 1


class c_byte(int):
    @staticmethod
    def size():
        return 1


class c_ubyte(int):
    @staticmethod
    def size():
        return 1


class c_short(int):
    @staticmethod
    def size():
        return 2


class c_ushort(int):
    @staticmethod
    def size():
        return 2


class c_int(int):
    @staticmethod
    def size():
        return 4


class c_uint(int):
    @staticmethod
    def size():
        return 4


class c_long(int):
    @staticmethod
    def size():
        return 8


class c_ulong(int):
    @staticmethod
    def size():
        return 8


class c_longlong(int):
    @staticmethod
    def size():
        return 16


class c_ulonglong(int):
    @staticmethod
    def size():
        return 16


class c_size_t(int):
    @staticmethod
    def size():
        return 8


class c_ssize_t(int):
    @staticmethod
    def size():
        return 8


class c_float(float):
    @staticmethod
    def size():
        return 4


class c_double(float):
    @staticmethod
    def size():
        return 8


class c_char_p():
    def __init__(cls, value=None):
        return c_char_p_(value)

    @staticmethod
    def size():
        return 8


class c_void_p(int):
    @staticmethod
    def size():
        return 8


def sizeof(t):
    return t.size()
