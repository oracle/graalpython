match a:
    case 'abc', *_: pass
    case 'def', *x: pass
    case (): pass
    case (42): pass     # not a sequence
    case (42,): pass
    case (()): pass
    case ((),): pass
    case ([]): pass
    case []: pass
    case [()]: pass
    case ['abc']: pass
    case ['def',]: pass
    case ['ghi', (*a,)]: pass
