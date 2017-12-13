(ns sparketbackend.states
  (:require [clojure.core.async :refer [put!]]
            [sparketbackend.customer :as cust]))

(defn next-state
  "Updates app-state to contain the state reached by transitioning from the
  current state."
  [app-state transition]
  (let [new-state (get-in cust/customer-fsm [(:state app-state) transition])]
    (assoc app-state :state new-state)))



