(ns neo4clj.cypher-test
  (:require [clojure.test :refer :all]
            [neo4clj.cypher :as sut]))

(deftest labels
  (testing "Generating a Cypher representaiton of labels"
    (are [cypher labels]
        (= cypher (sut/labels labels))
      "" []
      ":Address" [:address]
      ":Base:Address" [:address :base])))
