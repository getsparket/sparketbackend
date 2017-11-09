(ns sparketbackend.core
  (:require [sparketbackend.handler :as handler]
            [luminus.repl-server :as repl]
            [luminus.http-server :as http]
            [sparketbackend.config :refer [env]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [mount.core :as mount])
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
          'Ready {:getting-name-of-thing 'Do-Thing}})

(defn next-state
  "Updates app-state to contain the state reached by transitioning from the
 current state."
  [app-state transition]
  (let [new-state (get-in fsm [(:state app-state) transition])]
    (assoc app-state :state new-state)))

(defn get-most-similar-match
  [app-state supported-things]
  (first (second (first (group-by :similarity (get-list-of-similarities app-state supported-things))))))

(defn get-list-of-similarities
  "should return a list of maps of closest to furthest matches for a user-inputted-text in app-state"
  [app-state supported-things]
  (let [user-inputted (:user-inputted-text app-state)]
    (for [x supported-things]
      (assoc x :similarity (fuzzy/jaro user-inputted (:name x))))) )
(defn -main [& args]
  (start-app args))
