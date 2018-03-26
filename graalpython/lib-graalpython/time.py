def struct_time(*args, **kwargs):
    from collections import namedtuple

    nt = namedtuple("struct_time", [
        "tm_year",
        "tm_mon",
        "tm_mday",
        "tm_hour",
        "tm_min",
        "tm_sec",
        "tm_wday",
        "tm_yday",
        "tm_isdst",
    ])

    return nt(*args, **kwargs)


def gmtime(seconds):
    return struct_time(*__truffle_gmtime_tuple__(seconds))


def localtime(seconds):
    return struct_time(*__truffle_localtime_tuple__(seconds))
