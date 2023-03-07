import re

subject = 'abc'

literal_no_match = re.compile('def')
literal_match = re.compile('abc')
universal_match = re.compile('.')
char_class_match = re.compile('[a-z]')
char_class_no_match = re.compile('[0-9]')

def search_generic(regexp, num):
    for i in range(num):
        match = regexp.search(subject) is not None
    return match

def search_all(runs):
    for regexp in [literal_no_match, literal_match, universal_match, char_class_match, char_class_no_match]:
        match = search_generic(regexp, runs)
    return match

def __benchmark__(runs=20000000):
    match = search_all(runs)
    print("match", match)
