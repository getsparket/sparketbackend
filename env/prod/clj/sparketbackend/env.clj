(ns sparketbackend.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[sparketbackend started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[sparketbackend has shut down successfully]=-"))
   :middleware identity})
