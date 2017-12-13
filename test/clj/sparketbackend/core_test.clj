(ns sparketbackend.core-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [sparketbackend.handler :refer :all]
            [sparketbackend.twilio :as twil]
            [sparketbackend.core :as core]
            [clj-http.client :as hc]
            [clojure.core.async :as async]
            [mount.core :as mount]))

(use-fixtures
  :once
  (fn [f]
    (mount/start-without #'sparketbackend.core/repl-server)
    (f)))

(deftest blah
  (is (= 1 1)))

(deftest test-app
  (testing "main route"
    (let [response ((app) (request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response ((app) (request :get "/invalid"))]
      (is (= 404 (:status response))))))

#_(deftest can-identify-an-object
  (testing "totally"
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

      (is (= "Apple iPhone 6S 128GB" (:name (core/get-most-similar-match app-state supported-things)))))))

#_(deftest state-machine
  (let [fsm {'Start {:init 'Ready}
             'Ready {:getting-name-of-thing 'Identifying-Thing}
             'Identifying-Thing {:exact-match 'Exact-Match
                                 :inexact-match 'Inexact-Match}
             'Exact-Match {:zip-code 'Zip-Code}}
        user-inputted-text "Apple iPhone 6S 128GB"
        supported-things [{:name "Apple iPhone 6S 128GB"   :price 450}
                          {:name  "Apple iPhone 6S 64GB"   :price 400}
                          {:name  "Apple iPhone 6S 32GB"   :price 350}
                          {:name  "Apple iPhone 6S+ 128GB" :price 500}
                          {:name  "Apple iPhone 6S+ 64GB"  :price 550}
                          {:name  "Apple iPhone 6S+ 32GB"  :price 350}]]
    (testing "can do state machine thing"
      (let [app-state {:state 'Start}]
        (is (= {:state 'Ready} (core/next-state app-state :init)))))
    (testing "can, given some user input, change the state"
      (let [app-state {:state 'Identifying-Thing}]
        (is (= {:state 'Exact-Match :most-similar {:name "Apple iPhone 6S 128GB", :price 450, :similarity 1.0}} (core/handle-identifying-item app-state supported-things user-inputted-text)))))))

(deftest txt-state-machine
  (let [fsm {'Start {:init 'Ready}
             'Ready {:getting-name-of-thing 'Identifying-Thing}
             'Identifying-Thing {:exact-match 'Exact-Match
                                 :inexact-match 'Inexact-Match}
             'Exact-Match {:sent-offer 'Sent-Offer
                           :zip-code 'Zip-Code}}
        app-state {:state 'Start :value "want-to-extract"}
        in (async/chan)]
    (testing "can be in a state, put app-state on the channel, then pop something about app-state, then change state")))

(deftest txt ;; FIXME remove creds
  (let [])
  (testing "can send a text with the test API"
    (is (= 201 (:status (clj-http.client/post "https://api.twilio.com/2010-04-01/Accounts/AC59d0dd19a6c312c2ceda0697138e0c69/Messages"
                                              {:form-params {"To" "+18043382663"
                                                             "From" "+15005550006"
                                                             "Body" "testing"}
                                               :basic-auth "AC59d0dd19a6c312c2ceda0697138e0c69:0b3ae6707756861ce981827a4fd0fecb"})))))
  )

(deftest user-accounts-with-state-changes
  (with-redefs [twil/customer-accounts (atom {"18043382663"
                                              {:cust/address ""
                                               :cust/state 'Start
                                               :cust/things
                                               [{:thing/name "Apple iPhone 6S+ 32GB"
                                                 :thing/price "350"
                                                 :thing/state 'Exact-Match
                                                 :thing/txts []}]}})]
    (let [txt {:phone-number "18043382663"
               :body "I'd like to sell something"}]
      (testing "can update atom state based on txt"
        (is (= 'Ready (get-in (twil/do-thing-with-txt! txt) ["18043382663" :cust/state])))))))
