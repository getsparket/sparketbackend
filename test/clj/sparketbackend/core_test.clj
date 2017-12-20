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
    (mount/start-without #'sparketbackend.core/repl-server #_#'sparketbackend.twilio/http-loop)
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

(deftest twilio-test-api
  (testing "can send a text with the test API. this tests whether twilio is up."
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
                   :cust/things []
                   :cust/txts [txt]}}]
    (with-redefs [twil/customer-accounts (atom {dummy-phone-number
                                                {:cust/address ""
                                                 :cust/state 'Start
                                                 :cust/things []}})]
      (testing "first test that customer-accounts is different before tx"
        (is (not= after-tx @twil/customer-accounts)))
      (testing "now test that after do-thing-with-txt!, customer-accounts has desired values"
        (do (twil/do-thing-with-txt! txt)
            (is (= after-tx @twil/customer-accounts)))))))

(deftest integration
  (let [dummy-phone-number (:dummy-phone-number env)
        dev-sparket      (:twilio-dev-phone-number env)]
  (with-redefs [twil/customer-accounts (atom {})
                twil/dispatched-messages (atom #{})
                twil/get-most-recent-messages (fn [_ _] ;; mock function
                                                [{:from dummy-phone-number
                                                  :body "Hello!"
                                                  :to dev-sparket}])]
    (let [after-handle-start {dummy-phone-number
                              #:cust{:state 'Ready,
                                     :txts
                                     [{:from dummy-phone-number
                                       :body "Hello!"
                                       :to dev-sparket}]}}]
      (testing "intending to test that: given a new txt already picked up by the GET request, we should have created a new entry in the db with the correct arguments"
        (do
          (Thread/sleep 5500) ;; 5500 guarantees http-loop runs at least once.
          (is (= after-handle-start @twil/customer-accounts))))))))

(deftest customer-gets-thing
  (let [dummy-phone-number (:dummy-phone-number env)
        dev-sparket        (:twilio-dev-phone-number env)]
    (with-redefs [twil/customer-accounts (atom {})
                  twil/dispatched-messages (atom #{})]
      (let [start-txt [{:from dummy-phone-number
                         :body "Hello!"
                        :to dev-sparket}]
            ready-txt [{:from dummy-phone-number
                        :body "i'd like to sell an iphone 6s+ 32gb"
                        :to dev-sparket}]
            after-handle-start {dummy-phone-number
                                #:cust{:state 'Ready,
                                       :txts
                                       [{:from dummy-phone-number
                                         :body "Hello!"
                                         :to dev-sparket}]}}
            after-handle-ready {dummy-phone-number
                                #:cust{:state 'Identifying-Thing,
                                       :txts
                                       [{:from dummy-phone-number
                                         :body "Hello!"
                                         :to dev-sparket}
                                        {:from dummy-phone-number
                                         :body "i'd like to sell an iphone 6s+ 32gb"
                                         :to dev-sparket}]}}]
        (testing "intending to test that a customer can go from start to ready to identifying item"
          (do
            (twil/put!-new-messages start-txt)
            (Thread/sleep 100) ;; HACK async/put!, then later take! then later dispatch-new-messages takes a little time. TODO put these in a parking loop
            (is (= after-handle-start @twil/customer-accounts))
            (twil/put!-new-messages ready-txt)
            (Thread/sleep 100)
            (is (= after-handle-ready @twil/customer-accounts))))))))
