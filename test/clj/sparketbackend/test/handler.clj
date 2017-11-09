(ns sparketbackend.test.handler
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [sparketbackend.handler :refer :all]
            [sparketbackend.core :as core]))

(deftest test-app
  (testing "main route"
    (let [response ((app) (request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response ((app) (request :get "/invalid"))]
      (is (= 404 (:status response))))))

(deftest can-identify-an-object
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

(deftest state-machine
  (testing "can do state machine thing"
    (let [fsm {'Start {:init 'Ready}
               'Ready {:getting-name-of-thing 'Do-Thing}}
          app-state {:state 'Start}]
      (is (= {:state 'Ready} (core/next-state app-state :init))))))

