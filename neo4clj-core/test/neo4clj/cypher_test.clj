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
          cypher-single-key "((G__42.number = '12345678') OR (G__42.number = '87654321'))"
          cypher-multi-keys (str "((G__42.code = '+45' AND G__42.number = '12345678')"
                                 " OR "
                                 "(G__42.code = '+18' AND G__42.number = '87654321'))")]
      (are [cypher props]
          (= cypher (sut/where "G__42" props))
        "(G__42.number = '12345678')" {:number "12345678"}
        "(G__42.code = '+45' AND G__42.number = '12345678')" {:code "+45" :number "12345678"}
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
      "(p:Person)-[r:EMPLOYEE {hiredAt: 2008}]->(c:Company)" {:ref-id "r" :type :employee :props {:hired-at 2008}}
      "(p:Person)-[r:EMPLOYEE]->(c:Company)" {:ref-id "r" :type :employee :props [{:hired-at 2008} {:hired-at 2012}]})))

(deftest lookup-where
  (testing "Cypher representation of a where for a query"
    (let [single-node {:ref-id "n" :id 2 :props {:first-name "Neo" :last-name "Anderson"}}
          multi-node {:ref-id "n" :id 2 :props [{:first-name "Neo" :last-name "Anderson"} {:first-name "Agent" :last-name "Smith"}]}]
      (are [cypher where]
          (= cypher (sut/lookup-where where))
        nil {:ref-id "n"}
        "ID(n) = 2" {:ref-id "n" :id 2}
        "ID(n) = 2" single-node
        "(n.firstName = 'Neo' AND n.lastName = 'Anderson')" (dissoc single-node :id)
        "ID(n) = 2" multi-node
        "((n.firstName = 'Neo' AND n.lastName = 'Anderson') OR (n.firstName = 'Agent' AND n.lastName = 'Smith'))" (dissoc multi-node :id)))))


(deftest lookup-node
  (testing "Cypher representation of a Node lookup including where parts"
    (let [node {:ref-id "G__123" :labels [:customer :person] :props {:first-name "Neo" :last_name "Anderson"}}]
      (are [cypher-parts lookup]
          (= cypher-parts (sut/lookup-node lookup))
        ["(G__123)" nil] {:ref-id "G__123"}
        ["(G__123)" "ID(G__123) = 4"] {:ref-id "G__123" :id 4}
        ["(G__123:Person)" nil] {:ref-id "G__123" :labels [:person]}
        ["(G__123:Person:Customer)" nil] (dissoc node :props)
        ["(G__123 {firstName: 'Neo', lastName: 'Anderson'})" nil] (dissoc node :labels)
        ["(G__123:Person:Customer {firstName: 'Neo', lastName: 'Anderson'})" nil] node
        ["(G__123:Person:Customer)" "((G__123.firstName = 'Neo') OR (G__123.lastName = 'Anderson'))"] (assoc node :props [{:first-name "Neo"}
                                                                                                                {:last_name "Anderson"}])
        ["(G__123:Person:Customer)" "((G__123.firstName = 'Neo' AND G__123.lastName = 'Anderson') OR (G__123.firstName = 'Agent' AND G__123.lastName = 'Smith'))"] (assoc node :props [{:first-name "Neo" :last_name "Anderson"} {:first-name "Agent" :last_name "Smith"}])
        ["(G__123:Person:Customer)" "ID(G__123) = 4"] (assoc node :id 4)))))

(deftest lookup-relationship
  (testing "Cypher representation of a Relationship lookup including where parts"
    (let [rel-base {:ref-id "G__234" :from {:ref-id "G__123"} :to {:ref-id "G__345"}}
          rel-full (assoc rel-base :id 4 :type :enemy :props {:a 2 :b 4})
          rel-nodes {:ref-id "G__234" :from {:ref-id "G__123" :id 24} :to {:ref-id "G__345" :props {:a 1 :b 2}}}]
      (are [expected-cypher entity]
          (= expected-cypher (sut/lookup-relationship entity))
        ["(G__123)-[G__234]->(G__345)" nil] rel-base
        ["(G__123)-[G__234:ENEMY]->(G__345)" nil] (assoc rel-base :type :enemy)
        ["(G__123)-[G__234]->(G__345)" "ID(G__234) = 4"] (assoc rel-base :id 4)
        ["(G__123)-[G__234:ENEMY]->(G__345)" "ID(G__234) = 4"] (assoc rel-base :id 4 :type :enemy)
        ["(G__123)-[G__234]->(G__345)" "ID(G__234) = 4"] (dissoc rel-full :type)
        ["(G__123)-[G__234:ENEMY {a: 2, b: 4}]->(G__345)" nil] (dissoc rel-full :id)
        ["(G__123)-[G__234]->(G__345)" "((G__234.lastName = 'Smith') OR (G__234.lastName = 'Anderson'))"] (assoc rel-base :props [{:last-name "Anderson"} {:last-name "Smith"}])
        ["(G__123)-[G__234]->(G__345 {a: 1, b: 2})" "ID(G__123) = 24"] rel-nodes
        ["(G__123)-[G__234:ENEMY]->(G__345 {a: 1, b: 2})" "ID(G__234) = 4 AND ID(G__123) = 24"] (assoc rel-nodes :type :enemy :id 4)))))
