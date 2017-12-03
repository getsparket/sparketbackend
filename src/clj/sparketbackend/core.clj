(ns sparketbackend.core
  (:require [sparketbackend.handler :as handler]
            [luminus.repl-server :as repl]
            [luminus.http-server :as http]
            [sparketbackend.config :refer [env]]
            [sparketbackend.twilio :as twil]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [clj-fuzzy.metrics :as fuzzy]
            [clojure.core.async :as async])
  (:gen-class))


(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])

(mount/defstate ^{:on-reload :noop}
                http-server
                :start
                (http/start
                  (-> env
                      (assoc :handler (handler/app))
                      (update :port #(or (-> env :options :port) %))))
                :stop
                (http/stop http-server))

(mount/defstate ^{:on-reload :noop}
                repl-server
                :start
                (when-let [nrepl-port (env :nrepl-port)]
                  (repl/start {:port nrepl-port}))
                :stop
                (when repl-server
                  (repl/stop repl-server)))


(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))

(defn start-app [args]
  (doseq [component (-> args
                        (parse-opts cli-options)
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(def fsm {'Start {:init 'Ready}
          'Ready {:getting-name-of-thing 'Identifying-Thing}
          'Identifying-Thing {:exact-match 'Exact-Match
                              :inexact-match 'Inexact-Match}
          'Exact-Match {:accept-price? 'Accept-Price?}
          'Accept-Price? {:zip-code 'Zip-Code}})

(defn next-state
  "Updates app-state to contain the state reached by transitioning from the
 current state."
  [app-state transition]
  (let [new-state (get-in fsm [(:state app-state) transition])]
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

(defn handle-identifying-item [app-state supported-things user-inputted-text]
  (let [similar (get-most-similar-match (assoc app-state :user-inputted-text user-inputted-text) supported-things)
        in (async/chan)]
    (-> app-state
        (assoc :most-similar similar)
        (next-state :exact-match))))

(def text-chan (async/chan 10))

(defn handle-exact-match [app-state]
  (async/put! text-chan (str "you have a " (get-in app-state [:most-similar :name]) ". would you like " (get-in app-state [:most-similar :price]) " for it?"))
  (-> app-state
      (next-state :accept-price?)))


(defn txt-loop []
  (async/go-loop []
    (let [x (async/<! text-chan)]
      (println "testing actual texts" x)
      (twil/send-txt-message x "+18043382663"))
    (recur)))



(defn -main [& args]
  (start-app args))
