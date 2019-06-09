(ns neo4clj.cypher-test
  (:require [clojure.test :refer :all]
            [neo4clj.cypher :as sut]))

(deftest gen-ref-id
  (testing "Generate a unique reference id"
    (with-redefs [gensym (fn [] (str "G__123"))]
      (is (= "G__123" (sut/gen-ref-id))))))

(deftest where
  (testing "Cypher for where parts based on properties"
    (let [single-key-props-coll [{:number "12345678"} {:number "87654321"}]
          multi-key-props-coll [{:code "+45" :number "12345678"} {:code "+18" :number "87654321"}]
          cypher-single-key "G__42.number = '12345678' OR G__42.number = '87654321'"
          cypher-multi-keys (str "G__42.code = '+45' AND G__42.number = '12345678'"
                                 " OR "
                                 "G__42.code = '+18' AND G__42.number = '87654321'")]
      (are [cypher props]
          (= cypher (sut/where "G__42" props))
        "G__42.number = '12345678'" {:number "12345678"}
        "G__42.code = '+45' AND G__42.number = '12345678'" {:code "+45" :number "12345678"}
        cypher-single-key (set single-key-props-coll)
        cypher-multi-keys (set multi-key-props-coll)
        cypher-single-key single-key-props-coll
        cypher-multi-keys multi-key-props-coll
        cypher-single-key (apply list single-key-props-coll)
        cypher-multi-keys (apply list multi-key-props-coll)))))

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
