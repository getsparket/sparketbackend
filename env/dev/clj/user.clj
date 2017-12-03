(ns user
  (:require [mount.core :as mount]
            [sparketbackend.figwheel :refer [start-fw stop-fw cljs]]
            [sparketbackend.core :as core]
            [clojure.core.async :as async]
            [sparketbackend.twilio :refer :all]))

(defn start []
  (mount/start-without #'sparketbackend.core/repl-server)
  (load-file "src/clj/sparketbackend/twilio.clj") ;; TODO how to assign mount state's variables to clojure variables?
  (dispatch-new-messages)
  (http-loop))

(defn stop []
  (mount/stop-except #'sparketbackend.core/repl-server))

(defn restart []
  (stop)
  (start))


