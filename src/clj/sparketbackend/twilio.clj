(ns sparketbackend.twilio
  (:require [sparketbackend.handler :as handler]
            [sparketbackend.chans :as chans]
            [sparketbackend.fsm.handlers :as fh]
            [luminus.repl-server :as repl]
            [sparketbackend.config :refer [env]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [clojure.core.async :refer [put! go-loop <! >! onto-chan]]
            [clj-http.client :as hc]
            [clojure.data.json :as json]
            [clojure.set :as set])
  (:gen-class))


(defn send-txt-message [env body to]
  (let [sid          (:twilio-account-sid env)
        token        (:twilio-auth-token env)
        phone-number (:phone-number env)
        url          (str "https://api.twilio.com/2010-04-01/Accounts/"  sid "/Messages")
        basic-auth (str sid ":" token)]
    (hc/post url
               {:form-params {"To" to
                              "From" phone-number
                              "Body" body}
                :basic-auth basic-auth})))


(defn test-send-txt-message [env body to]
  (let [sid          (:twilio-test-account-sid env)
        token        (:twilio-test-auth-token env)
        phone-number (:test-phone-number env)
        url          (str "https://api.twilio.com/2010-04-01/Accounts/" sid "/Messages")
        basic-auth (str sid ":" token)]
    (hc/post url
             {:form-params {"To" to
                            "From" phone-number
                            "Body" body}
              :basic-auth basic-auth})))


(defn get-most-recent-messages [sid token]
  (let [url        (str "https://api.twilio.com/2010-04-01/Accounts/" sid "/Messages.json")
        basic-auth (str sid ":" token)]
    (-> (hc/get url {:as :json
                     :basic-auth basic-auth})
        :body
        :messages)))

(def dispatched-messages (atom #{}))

(defn most-recent-messages->new-messages
  "given the most recent messages, return only the ones that aren't stored. also, update stored messages"
  [recent-messages]
  (let [new-messages (set/difference (into #{} recent-messages) @dispatched-messages)]
    (swap! dispatched-messages set/union (into #{} recent-messages))
    new-messages))


(defn put!-new-messages [new-messages]
  (onto-chan chans/incoming new-messages false))

(def customer-accounts (atom {}))

(defn do-thing-with-txt!
  "Given a new SMS, do-thing-with-txt calls the handler associated with the customer's current state. It then updates the customer"
  [{:keys [phone-number] :as txt}]
  (let [customer         (get @customer-accounts phone-number)
        state            (:cust/state customer)
        updated-customer ((get fh/fsm->handler state) customer txt)]
    (swap! customer-accounts assoc-in [phone-number] updated-customer)))



(def txt-atom (atom #{}))

(defn dispatch-new-messages []
  (go-loop []
    (if-let [x (<! chans/incoming)]
      (do
        (spit "event.log" (str x "\n") :append true)
        (swap! txt-atom conj x)
        (do-thing-with-txt! x)
        ))
    (recur)))

(defn http-loop [{:keys [twilio-account-sid twilio-auth-token] :as opts}]
  (loop []
      (future (Thread/sleep 5000)
              (-> (get-most-recent-messages twilio-account-sid twilio-auth-token)
                  most-recent-messages->new-messages
                  put!-new-messages)
              (recur))))


(defn txt-loop [env]
  (go-loop []
    (let [x (<! chans/text-chan)]
      (println "testing actual texts" x)
      (send-txt-message env x "+18043382663"))
    (recur)))
