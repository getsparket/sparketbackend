(ns sparketbackend.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [sparketbackend.core-test]))

(doo-tests 'sparketbackend.core-test)

