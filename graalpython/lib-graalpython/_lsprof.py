from _descriptor import make_named_tuple_class

profiler_entry = make_named_tuple_class(
    "profiler_entry",
    ["code",
     "callcount",
     "reccallcount",
     "totaltime",
     "inlinetime",
     "calls"]
)


profiler_subentry = make_named_tuple_class(
    "profiler_subentry",
    ["code",
     "callcount",
     "reccallcount",
     "totaltime",
     "inlinetime"]
)


def make_wrapped_getstats(original):
    def getstats(self):
        stats = original(self)
        for idx, s in enumerate(stats):
            calls = s[-1]
            if calls:
                s[-1] = [profiler_subentry(c) for c in calls]
            stats[idx] = profiler_entry(s)
        return stats
    return getstats


Profiler.getstats = make_wrapped_getstats(Profiler.getstats)


# cleanup
del make_wrapped_getstats
del make_named_tuple_class
