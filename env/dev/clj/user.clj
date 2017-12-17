(ns user
  (:require [mount.core :as mount]
            [sparketbackend.figwheel :refer [start-fw stop-fw cljs]]
            [sparketbackend.core :as core]
            [clojure.core.async :refer :all]
            [sparketbackend.twilio :refer :all]
            [sparketbackend.customer :as cust]))

(defn start []
  (mount/start-without #'sparketbackend.core/repl-server))

(defn stop []
  (mount/stop-except #'sparketbackend.core/repl-server))

(defn restart []
  (stop)
  (start))
