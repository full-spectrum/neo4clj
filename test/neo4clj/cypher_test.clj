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

(deftest node
  (testing "Cypher representation of a node"
    (are [cypher-parts node]
        (= cypher-parts (sut/node node))
      "(n)" {:ref-id "n"}
      "(p:Person)" {:ref-id "p" :labels [:person]}
      "(c:Person:Customer)" {:ref-id "c" :labels [:customer :person]}
      "(c {firstName: 'Neo', lastName: 'Anderson'})" {:ref-id "c"
                                                      :props {:first-name "Neo"
                                                              :last_name "Anderson"}}
      "(c:Person:Customer {firstName: 'Neo', lastName: 'Anderson'})" {:ref-id "c"
                                                                      :labels [:customer :person]
                                                                      :props {:first-name "Neo"
                                                                                        :last_name "Anderson"}})))

(deftest relationship
  (testing "Cypher representation of a relationship"
    (are [cypher rel]
        (= cypher (sut/relationship "(p:Person)" "(c:Company)" rel))
      "(p:Person)-[]->(c:Company)" {}
      "(p:Person)-[r]->(c:Company)" {:ref-id "r"}
      "(p:Person)-[:EMPLOYEE]->(c:Company)" {:type :employee}
      "(p:Person)-[r:FORMER_EMPLOYEE]->(c:Company)" {:ref-id "r" :type :former-employee}
      "(p:Person)-[r {hiredAt: 2008}]->(c:Company)" {:ref-id "r" :props {:hired-at 2008}}
      "(p:Person)-[r:EMPLOYEE {hiredAt: 2008}]->(c:Company)" {:ref-id "r" :type :employee :props {:hired-at 2008}})))

(deftest lookup
  (testing "Cypher representation of a lookup entity including where parts"
    (are [cypher-parts lookup]
        (= cypher-parts (sut/lookup lookup))
      ["(n)" nil] {:ref-id "n"}
      ["(n)" "ID(n) = 12"] {:ref-id "n" :id 12}
      ["(p:Person)" nil] {:ref-id "p" :labels [:person]}
      ["(c:Person:Customer)" nil] {:ref-id "c" :labels [:customer :person]}
      ["(c:Person:Customer {firstName: 'Neo', lastName: 'Anderson'})" nil] {:ref-id "c"
                                                                            :labels [:customer :person]
                                                                            :props {:first-name "Neo"
                                                                                    :last_name "Anderson"}}
      ["(c:Person:Customer)" "(c.firstName = 'Neo' OR c.lastName = 'Anderson')"] {:ref-id "c"
                                                                                  :labels [:customer :person]
                                                                                  :props [{:first-name "Neo"}
                                                                                          {:last_name "Anderson"}]}
      ["(c:Person:Customer)" "ID(c) = 12"] {:ref-id "c" :labels [:customer :person] :id 12})))
