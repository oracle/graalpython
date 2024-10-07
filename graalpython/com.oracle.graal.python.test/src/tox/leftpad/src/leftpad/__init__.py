def leftpad(s, n):
    """ 
    Pads the string with spaces (from left) so that the resulting length is 'n'.
    Longer strings are left unchanged.
    """
    padlen = max(0, n - len(s))
    return ' ' * padlen + s
