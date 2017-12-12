(ns sparketbackend.core
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

(mount/defstate ^{:on-reload :noop}
  twilio
  :start
  (do
    (twil/http-loop env)
    (twil/dispatch-new-messages))
  :stop
  nil ;; FIXME what goes here? how to remove references to go loops?
  )


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



#_(defn txt-loop []
  (async/go-loop []
    (let [x (async/<! text-chan)]
      (println "testing actual texts" x)
      (twil/send-txt-message x "+18043382663"))
    (recur)))

(defn -main [& args]
  (start-app args))
