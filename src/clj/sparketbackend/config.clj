(ns sparketbackend.config
  (:require [cprop.core :refer [load-config]]
            [cprop.source :as source]
            [mount.core :refer [args defstate]]))

(defstate env :start (load-config
                       :merge
                       [(args)
                        (source/from-system-props)
                        (source/from-env)
                        (source/from-file "secrets.edn")])) ;; get this file from matt. that is, do not keep secrets in version control

