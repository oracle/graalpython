# Copyright (c) 2018, 2019, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

import unittest
import random
import time


class TestBasicOps:
    def __init__(self):
        super().__init__()
        self.gen = random.Random()

    def randomlist(self, n):
        """Helper function to make a list of random numbers"""
        return [self.gen.random() for i in range(n)]

    def test_autoseed(self):
        self.gen.seed()
        state1 = self.gen.getstate()
        time.sleep(0.1)
        self.gen.seed()      # diffent seeds at different times
        state2 = self.gen.getstate()
        self.assertNotEqual(state1, state2)

    def test_saverestore(self):
        N = 1000
        self.gen.seed()
        state = self.gen.getstate()
        randseq = self.randomlist(N)
        self.gen.setstate(state)    # should regenerate the same sequence
        self.assertEqual(randseq, self.randomlist(N))

    def test_seedargs(self):
        # Seed value with a negative hash.
        class MySeed(object):
            def __hash__(self):
                return -1729
        for arg in [None, 0, 0, 1, 1, -1, -1, 10**20, -(10**20),
                    3.14, 1+2j, 'a', tuple('abc'), MySeed()]:
            self.gen.seed(arg)
        for arg in [list(range(3)), dict(one=1)]:
            self.assertRaises(TypeError, self.gen.seed, arg)
        self.assertRaises(TypeError, self.gen.seed, 1, 2, 3, 4)
        self.assertRaises(TypeError, type(self.gen), [])

    def test_shuffle(self):
        shuffle = self.gen.shuffle
        lst = []
        shuffle(lst)
        self.assertEqual(lst, [])
        lst = [37]
        shuffle(lst)
        self.assertEqual(lst, [37])
        seqs = [list(range(n)) for n in range(10)]
        shuffled_seqs = [list(range(n)) for n in range(10)]
        for shuffled_seq in shuffled_seqs:
            shuffle(shuffled_seq)
        for (seq, shuffled_seq) in zip(seqs, shuffled_seqs):
            self.assertEqual(len(seq), len(shuffled_seq))
            self.assertEqual(set(seq), set(shuffled_seq))
        # The above tests all would pass if the shuffle was a
        # no-op. The following non-deterministic test covers that.  It
        # asserts that the shuffled sequence of 1000 distinct elements
        # must be different from the original one. Although there is
        # mathematically a non-zero probability that this could
        # actually happen in a genuinely random shuffle, it is
        # completely negligible, given that the number of possible
        # permutations of 1000 objects is 1000! (factorial of 1000),
        # which is considerably larger than the number of atoms in the
        # universe...
        lst = list(range(1000))
        shuffled_lst = list(range(1000))
        shuffle(shuffled_lst)
        self.assertTrue(lst != shuffled_lst)
        shuffle(lst)
        self.assertTrue(lst != shuffled_lst)

    # def test_choice(self):
    #     choice = self.gen.choice
    #     with self.assertRaises(IndexError):
    #         choice([])
    #     self.assertEqual(choice([50]), 50)
    #     self.assertIn(choice([25, 75]), [25, 75])

    def test_sample(self):
        # For the entire allowable range of 0 <= k <= N, validate that
        # the sample is of the correct length and contains only unique items
        N = 100
        population = range(N)
        for k in range(N+1):
            s = self.gen.sample(population, k)
            self.assertEqual(len(s), k)
            uniq = set(s)
            self.assertEqual(len(uniq), k)
            self.assertTrue(uniq <= set(population))
        self.assertEqual(self.gen.sample([], 0), [])  # test edge case N==k==0
        # Exception raised if size of sample exceeds that of population
        self.assertRaises(ValueError, self.gen.sample, population, N+1)
        self.assertRaises(ValueError, self.gen.sample, [], -1)

    def test_sample_distribution(self):
        # For the entire allowable range of 0 <= k <= N, validate that
        # sample generates all possible permutations
        n = 5
        pop = range(n)
        trials = 10000  # large num prevents false negatives without slowing normal case
        def factorial(n):
            if n == 0:
                return 1
            return n * factorial(n - 1)
        for k in range(n):
            expected = factorial(n) // factorial(n-k)
            perms = {}
            for i in range(trials):
                perms[tuple(self.gen.sample(pop, k))] = None
                if len(perms) == expected:
                    break
            else:
                self.fail()

    def test_sample_inputs(self):
        # SF bug #801342 -- population can be any iterable defining __len__()
        self.gen.sample(set(range(20)), 2)
        self.gen.sample(range(20), 2)
        self.gen.sample(range(20), 2)
        self.gen.sample(str('abcdefghijklmnopqrst'), 2)
        self.gen.sample(tuple('abcdefghijklmnopqrst'), 2)

    def test_sample_on_dicts(self):
        self.assertRaises(TypeError, self.gen.sample, dict.fromkeys('abcdef'), 2)

    def test_choices(self):
        import sys
        return # TODO: re-enable once we update again
        if sys.version_info.minor < 7:
            return

        choices = self.gen.choices
        data = ['red', 'green', 'blue', 'yellow']
        str_data = 'abcd'
        range_data = range(4)
        set_data = set(range(4))

        # basic functionality
        for sample in [
            choices(data, k=5),
            choices(data, range(4), k=5),
            choices(k=5, population=data, weights=range(4)),
        ]:
            self.assertEqual(len(sample), 5)
            self.assertEqual(type(sample), list)
            self.assertTrue(set(sample) <= set(data))

    def test_gauss(self):
        # Ensure that the seed() method initializes all the hidden state.  In
        # particular, through 2.2.1 it failed to reset a piece of state used
        # by (and only by) the .gauss() method.

        for seed in 1, 12, 123, 1234, 12345, 123456, 654321:
            self.gen.seed(seed)
            x1 = self.gen.random()
            y1 = self.gen.gauss(0, 1)

            self.gen.seed(seed)
            x2 = self.gen.random()
            y2 = self.gen.gauss(0, 1)

            self.assertEqual(x1, x2)
            self.assertEqual(y1, y2)

    def test_bug_9025(self):
        # Had problem with an uneven distribution in int(n*random())
        # Verify the fix by checking that distributions fall within expectations.
        n = 100000
        randrange = self.gen.randrange
        k = sum(randrange(6755399441055744) % 3 == 2 for i in range(n))
        self.assertTrue(0.30 < k/n < .37, (k/n))
