(ns sparketbackend.core-test
  (:require [cljs.test :refer-macros [is are deftest testing use-fixtures]]
            [pjstadig.humane-test-output]
            [sparketbackend.core :as rc]))

(deftest test-home
  (is (= true true)))

