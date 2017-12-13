(ns sparketbackend.states
  (:require [clojure.core.async :refer [put!]]
            [sparketbackend.customer :as cust]))

(defn next-state
  "Updates app-state to contain the state reached by transitioning from the
  current state."
  [app-state transition]
  (let [new-state (get-in cust/customer-fsm [(:cust/state app-state) transition])]
    (assoc app-state :cust/state new-state)))



