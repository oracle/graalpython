# Run with HPY=trace
from hpy.trace import get_call_counts
import snippets

add_count_0 = get_call_counts()["ctx_Add"]
snippets.add(1, 2) == 3
add_count_1 = get_call_counts()["ctx_Add"]

print('get_call_counts()["ctx_Add"] == %d' % (add_count_1 - add_count_0))
