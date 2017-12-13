(ns sparketbackend.chans
  (:require [clojure.core.async :refer [chan]]))

;; lots of namespaces depend on these chans.
;; to avoid circular dependencies, put chans here.

(def text-chan (chan))
(def incoming (chan 100))


