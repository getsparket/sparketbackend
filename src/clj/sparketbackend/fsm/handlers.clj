(ns sparketbackend.fsm.handlers
  (:require [clojure.core.async :refer [put!]]
            [sparketbackend.customer :as cust]
            [sparketbackend.states :as states]
            [sparketbackend.chans :as chans]))

;; the general form for handlers is: side-effect (SMS), and return the customer-map with updated state and data
;; because twil/do-thing-with-txt! updates the twil/customer-accounts atom with the updated value.
(defn handle-start
  "to handle start, we tell the user: What do you have to sell to me?."
  [cust txt]
  (print cust txt "the state is started")
  ;; put a text message on the channel. return an updated state. state transitions should do nothing.
  (put! chans/text-chan (get cust/txts 'Start))
  ;; TODO error handling?
  (-> cust
      (states/next-state 'Ready)
      (update :cust/txts conj txt)))

(defn handle-ready [cust txt] ;; it should tell next-state which state it should go to
  (print cust txt "the state is ready")
  cust)

(defn identifying-thing [cust txt]
  (print cust txt "the state is identifying")
  cust)

(def fsm->handler
  {nil    #'handle-start
   'Start #'handle-start
   'Ready #'handle-ready
   'Identifying-Thing #'identifying-thing})

