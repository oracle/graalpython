import unittest
import test._test_multiprocessing

test._test_multiprocessing.install_tests_in_module_dict(globals(), 'graalpy')

if __name__ == '__main__':
    unittest.main()
