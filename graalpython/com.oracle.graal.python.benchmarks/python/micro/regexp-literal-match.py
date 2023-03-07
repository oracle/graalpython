import re

subject = 'abc'

literal_match = re.compile('abc')

def search_literal_match(runs):
    for i in range(runs):
        match = literal_match.search(subject) is not None
    return match

def __benchmark__(runs=100000000):
    match = search_literal_match(runs)
    print("match", match)
