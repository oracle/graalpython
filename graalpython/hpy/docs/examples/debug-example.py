# Run with HPY=debug
import hpy.debug
import snippets

hpy.debug.set_handle_stack_trace_limit(16)
from hpy.debug.pytest import LeakDetector
with LeakDetector() as ld:
    snippets.test_leak_stacktrace()

print("SUCCESS")  # Should not be actually printed
