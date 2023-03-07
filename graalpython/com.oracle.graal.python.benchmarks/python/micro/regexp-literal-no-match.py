import re

subject = 'abc'

literal_no_match = re.compile('def')

def search_literal_no_match(runs):
    for i in range(runs):
        match = literal_no_match.search(subject) is not None
    return match

def __benchmark__(runs=100000000):
    match = search_literal_no_match(runs)
    print("match", match)
