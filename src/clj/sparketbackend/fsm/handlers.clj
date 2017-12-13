(ns sparketbackend.fsm.handlers
  (:require [clojure.core.async :refer [put!]]
            [sparketbackend.customer :as cust]
            [sparketbackend.chans :as chans]))


(defn handle-start
  "to handle start, we tell the user: What do you have to sell to me?."
  [cust txt]
  (print cust txt "the state is started")
  ;; put a text message on the channel. return an updated state. state transitions should do nothing.
  (put! chans/text-chan (get cust/txts 'Start))
  cust)

(defn handle-ready [cust txt]
  (print cust txt "the state is ready")
  cust)

(defn indentifying-thing [cust txt]
  (print cust txt "the state is identifying")
  cust)

(def fsm->handler
  {'Start #'handle-start
   'Ready #'handle-ready
   'Identifying-Thing #'indentifying-thing})

