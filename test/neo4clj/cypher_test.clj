(ns neo4clj.cypher-test
  (:require [clojure.test :refer :all]
            [neo4clj.cypher :as sut]))

(deftest gen-ref-id
  (testing "Generate a unique reference id"
    (with-redefs [gensym (fn [] (str "G__123"))]
      (is (= "G__123" (sut/gen-ref-id))))))

(deftest properties
  (testing "Cypher representation of property map"
    (are [cypher props]
        (= cypher (sut/properties props))
      nil nil
      " {}" {}
      " {a: 1, b: 'test', c: TRUE}" {:a 1 :b "test" :c true})))

(deftest labels
  (testing "Generating a Cypher representaiton of labels"
    (are [cypher labels]
        (= cypher (sut/labels labels))
      "" []
      ":Address" [:address]
      ":Base:Address" [:address :base])))
