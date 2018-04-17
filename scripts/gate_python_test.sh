# mx --strict-compliance --dynamicimports sulong --primary gate --tags python-junit,python-unittest
# 
# DEFAULT_DYNAMIC_IMPORTS="sulong,/compiler"
mx --strict-compliance --dynamicimports sulong,/compiler --primary gate --tags python-unittest -B=--force-deprecation-as-warning-for-dependencies
# mx --dynamicimports /compiler python-gate --tags python-junit,python-unittest
