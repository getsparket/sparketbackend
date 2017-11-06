(ns ^:figwheel-no-load sparketbackend.app
  (:require [sparketbackend.core :as core]
            [devtools.core :as devtools]))

(enable-console-print!)

(devtools/install!)

(core/init!)
