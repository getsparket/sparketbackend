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




(defn send-txt-message [body to]
  (let [prod-sid (:twilio-account-sid env)
        prod-token (:twilio-auth-token env)
        phone-number (:phone-number env)
        url (str "https://api.twilio.com/2010-04-01/Accounts/"  prod-sid "/Messages")
        basic-auth (str prod-sid ":" prod-token)]
    (hc/post url
               {:form-params {"To" to
                              "From" phone-number
                              "Body" body}
                :basic-auth basic-auth})))


#_(defn test-send-txt-message [body to]
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
  "figure out what should be done with new txt, then do it"
  [{:keys [phone-number] :as txt}]
  ;; all we should have to do is call the handler function associated with the the current state of the user, and pass in the body of the text.
  ;; app-state has to be an atom. and things change only on receipt of txt messages.
  ;; (1) find the customer's state
  ;; (3) call the state-handler
  ;; (4) merge the updated state into customer-accounts
  ;; the state handlers should return the thing we want to merge with the atom.
  (let [cus-map (get @customer-accounts phone-number)
        cus-state (:cust/state cus-map)
        #_new-state #_(cus-map->new-state cus-map cus-state)]
    #_(swap! customer-accounts update ["blah" "blah"] new-state)
    ((get fh/fsm->handler cus-state) cus-map txt)
    cus-state))



(def txt-atom (atom #{}))

(defn dispatch-new-messages []
  (go-loop []
    (if-let [x (<! chans/incoming)]
      (do
        (spit "event.log" (str x "\n") :append true)
        (swap! txt-atom conj x)
        ;;(do-thing-with-txt x)
        ))
    (recur)))

(defn http-loop [{:keys [twilio-account-sid twilio-auth-token] :as opts}]
  (loop []
      (future (Thread/sleep 5000)
              (-> (get-most-recent-messages twilio-account-sid twilio-auth-token)
                  most-recent-messages->new-messages
                  put!-new-messages)
              (recur))))

(defn txt-loop []
  (go-loop []
    (let [x (<! chans/text-chan)]
      (println "testing actual texts" x)
      (send-txt-message x "+18043382663"))
    (recur)))


