(ns sparketbackend.core-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [sparketbackend.handler :refer :all]
            [sparketbackend.twilio :as twil]
            [sparketbackend.core :as core]
            [clj-http.client :as http]
            [clojure.core.async :as async]
            [mount.core :as mount]
            [sparketbackend.config :refer [env]]
            [sparketbackend.customer :as cust]))

(use-fixtures
  :once
  (fn [f]
    (mount/start-without #'sparketbackend.core/repl-server)
    (f)))

(deftest test-app
  (testing "main route"
    (let [response ((app) (request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response ((app) (request :get "/invalid"))]
      (is (= 404 (:status response))))))

(deftest can-identify-an-object
  (let [app-state {:user-inputted-text "Apple iPhone 6S 128GB"
                   :phone-number "+18043382663"
                   :thing-name "ipad"
                   :thing-price 99
                   :num-times-gone-through 1}
        supported-things [{:name "Apple iPhone 6S 128GB"  :price 450}
                          {:name  "Apple iPhone 6S 64GB"  :price 400}
                          {:name  "Apple iPhone 6S 32GB"  :price 350}
                          {:name  "Apple iPhone 6S+ 128GB" :price 500}
                          {:name  "Apple iPhone 6S+ 64GB"  :price 550}
                          {:name  "Apple iPhone 6S+ 32GB"  :price 350}]]

    (is (= "Apple iPhone 6S 128GB" (:name (cust/get-most-similar-match app-state supported-things))))))

(deftest txt
  (testing "can send a text with the test API"
    (let [sid                      (:twilio-test-account-sid env)
          token                    (:twilio-test-auth-token env)
          twilio-test-phone-number (:test-phone-number env)
          dummy-phone-number       (:dummy-phone-number env)]
      (is (= 201 (:status (http/post (str "https://api.twilio.com/2010-04-01/Accounts/" sid "/Messages")
                                                {:form-params {"To" dummy-phone-number
                                                               "From" twilio-test-phone-number
                                                               "Body" "testing"}
                                                 :basic-auth (str sid ":" token)})))))))

(deftest user-accounts-with-state-changes
  (let [dummy-phone-number (:dummy-phone-number env)
        txt {:phone-number dummy-phone-number
             :body "I'd like to sell something"
             :from dummy-phone-number}
        after-tx {dummy-phone-number
                  {:cust/address ""
                   :cust/state 'Ready
                   :cust/things
                   [{:thing/name "Apple iPhone 6S+ 32GB"
                     :thing/price "350"
                     :thing/state 'Exact-Match
                     :thing/txts []}]
                   :cust/txts [txt]}}]
    (with-redefs [twil/customer-accounts (atom {dummy-phone-number
                                                {:cust/address ""
                                                 :cust/state 'Start
                                                 :cust/things
                                                  [{:thing/name "Apple iPhone 6S+ 32GB"
                                                    :thing/price "350"
                                                    :thing/state 'Exact-Match
                                                    :thing/txts []}]}})]
      (testing "can update atom state based on txt"
        (is (= after-tx (twil/do-thing-with-txt! txt) #_(get-in (twil/do-thing-with-txt! txt)
                              [dummy-phone-number :cust/state])))))))
