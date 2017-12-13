(ns sparketbackend.customer
  (:require [sparketbackend.config :refer [env]]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [clojure.core.async :as async :refer [put! chan]]
            [clj-fuzzy.metrics :as fuzzy])
  (:gen-class))

(def txts {'Start "Welcome to Sparket. Ready to take your order!"
           'Ready "Thanks for telling me that. I'm figuring out what you said!" ;; could be a function
           'Identifying-Thing "Something goes here!"})

(def user-accounts (atom {}))

(def customer-fsm {nil                #{'Start} ;; HACK nil means phone-number doesn't exist in customer-acounts. see do-thing-with-txt!
                   'Start             #{'Ready}
                   'Ready             #{'Identifying-Thing}
                   'Identifying-Thing #{'Exact-Match 'Inexact-Match}
                   'Exact-Match       #{'Accept-Price?}
                   'Accept-Price?     #{'Zip-Code}})

(defn get-list-of-similarities
  "should return a list of maps of closest to furthest matches for a user-inputted-text in app-state"
  [app-state supported-things]
  (let [user-inputted (:user-inputted-text app-state)]
    (for [x supported-things]
      (assoc x :similarity (fuzzy/jaro user-inputted (:name x))))))

