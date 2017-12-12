(ns sparketbackend.customer
  (:require [sparketbackend.handler :as handler]
            [sparketbackend.twilio :as twil]
            [luminus.repl-server :as repl]
            [luminus.http-server :as http]
            [sparketbackend.config :refer [env]]
            [sparketbackend.twilio :as twil]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [clj-fuzzy.metrics :as fuzzy]
            [clojure.core.async :as async :refer [put! chan]])
  (:gen-class))


(def user-accounts (atom {}))

(def customer-fsm {'Start             {:init 'Ready}
                   'Ready             {:getting-name-of-thing 'Identifying-Thing}
                   'Identifying-Thing {:exact-match 'Exact-Match
                                       :inexact-match 'Inexact-Match}
                   'Exact-Match       {:accept-price? 'Accept-Price?}
                   'Accept-Price?     {:zip-code 'Zip-Code}})

(def fsm->handler
  {'Start #'handle-start
   'Ready #'handle-ready
   'Identifying-Thing #'identifying-thing})

(defn next-state
  "Updates app-state to contain the state reached by transitioning from the
  current state."
  [app-state transition]
  (let [new-state (get-in customer-fsm [(:state app-state) transition])]
    (assoc app-state :state new-state)))

(defn get-list-of-similarities
  "should return a list of maps of closest to furthest matches for a user-inputted-text in app-state"
  [app-state supported-things]
  (let [user-inputted (:user-inputted-text app-state)]
    (for [x supported-things]
      (assoc x :similarity (fuzzy/jaro user-inputted (:name x))))))

(defn get-most-similar-match
  [app-state supported-things]
  (first (second (first (group-by :similarity (get-list-of-similarities app-state supported-things))))))

(def text-chan (chan))

(defn handle-start
  "send a txt to phone-number to start user in flow. phone-number is currently the unique identifier"
  [customer-accounts phone-number]
  (let [customer-account (get @customer-accounts phone-number)
        updated          (assoc customer-account :cust/state 'Ready)
        body             (get twil/txts 'Start)]
    ;; (twil/send-txt-message body phone-number)
    (swap! customer-accounts assoc-in [phone-number :cust/state] 'Ready)
    ))

(defn handle-identifying-item [app-state supported-things user-inputted-text]
  (let [similar (get-most-similar-match (assoc app-state :user-inputted-text user-inputted-text) supported-things)
        in (chan)]
    (-> app-state
        (assoc :most-similar similar)
        (next-state :exact-match))))


(defn handle-exact-match [app-state]
  (put! text-chan (str "you have a " (get-in app-state [:most-similar :name]) ". would you like " (get-in app-state [:most-similar :price]) " for it?"))
  (-> app-state
      (next-state :accept-price?)))

