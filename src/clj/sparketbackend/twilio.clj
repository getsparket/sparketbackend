(ns sparketbackend.twilio
  (:require [sparketbackend.handler :as handler]
            [luminus.repl-server :as repl]
            [sparketbackend.config :refer [env]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [clj-fuzzy.metrics :as fuzzy]
            [clojure.core.async :as async]
            [clj-http.client :as hc]
            [clojure.data.json :as json]
            [clojure.set :as set])
  (:gen-class))

(def prod-sid          (:twilio-account-sid env))
(def prod-token        (:twilio-auth-token env))
(def phone-number      (:phone-number env))

(def test-sid          (:twilio-test-account-sid env))
(def test-token        (:twilio-test-auth-token env))
(def test-phone-number (:test-phone-number env))


(defn send-txt-message [body to]
  (let [url (str "https://api.twilio.com/2010-04-01/Accounts/" prod-sid "/Messages")
        basic-auth (str prod-sid ":" prod-token)]
    (hc/post url
               {:form-params {"To" to
                              "From" phone-number
                              "Body" body}
                :basic-auth basic-auth})))


(defn test-send-txt-message [body to]
  (let [url (str "https://api.twilio.com/2010-04-01/Accounts/" test-sid "/Messages")
        basic-auth (str test-sid ":" test-token)]
    (hc/post url
             {:form-params {"To" to
                            "From" test-phone-number
                            "Body" body}
              :basic-auth basic-auth})))


(defn get-most-recent-messages [prod-sid prod-token]
  (let [url (str "https://api.twilio.com/2010-04-01/Accounts/" prod-sid "/Messages.json")
        basic-auth (str prod-sid ":" prod-token)]
    (-> (hc/get url {:as :json
                     :basic-auth basic-auth})
        :body
        :messages)))

(def dispatched-messages (atom #{}))

(def incoming (async/chan 100))

(def user-accounts (atom {}))

(defn most-recent-messages->new-messages
  "given the most recent messages, return only the ones that aren't stored. also, update stored messages"
  [recent-messages]
  (let [new-messages (set/difference (into #{} recent-messages) @dispatched-messages)]
    (swap! dispatched-messages set/union (into #{} recent-messages))
    new-messages))


(defn put!-new-messages [new-messages]
  (async/onto-chan incoming new-messages false))


(defn dispatch-new-messages []
  (async/go-loop []
    (if-let [x (async/<! incoming)]
      (spit "event.log" (str x "\n") :append true)) ;; stub for txt response
    (recur)))

(defn http-loop [{:keys [twilio-account-sid twilio-auth-token] :as opts}]
  (async/go-loop []
      (async/<! (async/timeout 5000)) ;; can take this out once the get request is reliably asynchronous
      (-> (get-most-recent-messages twilio-account-sid twilio-auth-token)
          most-recent-messages->new-messages
          put!-new-messages)
      (recur)))

