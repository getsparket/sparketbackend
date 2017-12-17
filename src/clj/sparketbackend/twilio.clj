(ns sparketbackend.twilio
  (:require [sparketbackend.handler :as handler]
            [luminus.repl-server :as repl]
            [sparketbackend.customer :as cust]
            [sparketbackend.config :refer [env]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [clojure.core.async :refer [put! go-loop <! >! onto-chan timeout alts! thread chan close!]]
            [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.set :as set])
  (:gen-class))

(declare txt-dispatcher http-loop put!-new-messages fsm->handler) ;; HACK these are mutually-dependent

(defn send-txt-message [env body to]
  (let [sid          (:twilio-account-sid env)
        token        (:twilio-auth-token env)
        phone-number (:phone-number env)
        url          (str "https://api.twilio.com/2010-04-01/Accounts/"  sid "/Messages")
        basic-auth (str sid ":" token)]
    (http/post url
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
    (http/post url
             {:form-params {"To" to
                            "From" phone-number
                            "Body" body}
              :basic-auth basic-auth})))


(defn get-most-recent-messages [sid token]
  (let [url        (str "https://api.twilio.com/2010-04-01/Accounts/" sid "/Messages.json")
        basic-auth (str sid ":" token)]
    (-> (http/get url {:as :json
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



(def customer-accounts (atom {}))

(defn do-thing-with-txt!
  "Given a new SMS, do-thing-with-txt calls the handler associated with the customer's current state. It then updates the customer"
  [{:keys [to] :as txt}]
  (let [customer         (get @customer-accounts to)
        state            (:cust/state customer)
        updated-customer ((get fsm->handler state) customer txt)]
    (swap! customer-accounts assoc-in [to] updated-customer)))



(def txt-atom (atom #{}))

(defn dispatch-new-messages []
  (let [incoming (chan)]
    (go-loop [x (<! incoming)]
      (when x
        (do
          (spit "event.log" (str x "\n") :append true)
          (swap! txt-atom conj x)
          (do-thing-with-txt! x)))
      (recur (<! incoming)))
    incoming))

(mount/defstate ^{:on-reload :noop}
  txt-dispatcher
  :start (dispatch-new-messages)
  :stop (close! txt-dispatcher))

(defn put!-new-messages [new-messages]
  (onto-chan txt-dispatcher new-messages false))

(defn http-loop [{:keys [twilio-account-sid twilio-auth-token] :as opts}]
  (let [http-chan (chan)]
    (go-loop []
      (let  [[_ continue?] (alts! [(timeout 5000) http-chan])]
        (when-not (= continue? http-chan)
          (<! (thread (-> (get-most-recent-messages twilio-account-sid twilio-auth-token)
                          most-recent-messages->new-messages
                          put!-new-messages)))
          (recur))))
    http-chan))


(defn txt-loop [env]
  (let [txt-chan (chan)]
    (go-loop [body (<! txt-chan)]
      (when body
        (print "testing txt-loop")
        #_(send-txt-message env body "+18043382663")
        (recur (<! txt-chan))))
    txt-chan))


(mount/defstate ^{:on-reload :noop}
  new-txt-listener
  :start (http-loop env)
  :stop (close! new-txt-listener))


(mount/defstate ^{:on-reload :noop}
  new-txt-sender
  :start (txt-loop env)
  :stop (close! new-txt-sender))


(defn next-state
  "Updates app-state to contain the state reached by transitioning from the
  current state."
  [app-state transition]
  (let [new-state (get-in cust/customer-fsm [(:cust/state app-state) transition])]
    (assoc app-state :cust/state new-state)))

;; the general form for handlers is: side-effect (SMS), and return the customer-map with updated state and data
;; because do-thing-with-txt! updates the customer-accounts atom with the updated value.
(defn handle-start
  "to handle start, we tell the user: What do you have to sell to me?."
  [cust txt]
  (print "the state is started")
  ;; put a text message on the channel. return an updated state. state transitions should do nothing.
  (put! new-txt-sender (get cust/txts 'Start))
  ;; TODO error handling?
  (-> cust
      (assoc :cust/state 'Ready)
      (update :cust/txts conj txt)))

(defn handle-ready [cust txt] ;; placeholder
  (print "the state is ready")
  cust)

(defn identifying-thing [cust txt] ;; placeholder
  (print "the state is identifying")
  cust)

(def fsm->handler
  {nil    #'handle-start
   'Start #'handle-start
   'Ready #'handle-ready
   'Identifying-Thing #'identifying-thing})
