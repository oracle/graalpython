import re

subject = 'abc'

universal_match = re.compile('.')

def search_universal_match(runs):
    for i in range(runs):
        match = universal_match.search(subject) is not None
    return match

def __benchmark__(runs=100000000):
    match = search_universal_match(runs)
    print("match", match)
