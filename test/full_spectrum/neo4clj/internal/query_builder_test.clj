(ns full-spectrum.neo4clj.internal.query-builder-test
  (:require [clojure.test :as t]
            [full-spectrum.neo4clj.internal.query-builder :as sut]))

(t/deftest generate-ref-id
  (t/testing "Generate a unique reference id"
    (with-redefs [gensym (fn [] (str "G__123"))]
      (t/is (= "G__123" (sut/generate-ref-id))))))

(t/deftest properties-query
  (t/testing "Cypher representation of property map"
    (t/are [cypher props]
        (= cypher (sut/properties-query props))
      "{}" {}
      "{a: 1, b: 'test', c: TRUE}" {"a" 1 "b" "'test'" "c" "TRUE"})))

(t/deftest create-node-query
  (t/testing "Cypher for creating a node with/without return"
    (let [node {:ref-id "G__123" :labels [:label1] :props {:a "1"}}]
      (t/are [query return?]
          (= query (sut/create-node-query node return?))
        "CREATE (G__123:Label1 {a: '1'})" false
        "CREATE (G__123:Label1 {a: '1'}) RETURN G__123" true))))

(t/deftest where-query
  (t/testing "Cypher for where parts based on properties"
    (let [single-key-props-coll [{:number "12345678"} {:number "87654321"}]
          multi-key-props-coll [{:code "+45" :number "12345678"} {:code "+18" :number "87654321"}]
          cypher-single-key "G__42.number = '12345678' OR G__42.number = '87654321'"
          cypher-multi-keys (str "G__42.code = '+45' AND G__42.number = '12345678'"
                                 " OR "
                                 "G__42.code = '+18' AND G__42.number = '87654321'")]
      (t/are [expected-cypher props]
          (= expected-cypher (sut/where-query "G__42" props))
        "G__42.number = '12345678'" {:number "12345678"}
        "G__42.code = '+45' AND G__42.number = '12345678'" {:code "+45" :number "12345678"}
        cypher-single-key (set single-key-props-coll)
        cypher-multi-keys (set multi-key-props-coll)
        cypher-single-key single-key-props-coll
        cypher-multi-keys multi-key-props-coll
        cypher-single-key (apply list single-key-props-coll)
        cypher-multi-keys (apply list multi-key-props-coll)))))

(t/deftest lookup-query
  (t/testing "Cypher for lookup query"
    (let [node {:ref-id "G__123"
                :labels [:label1 :label2]
                :props {:a "1" :b "2"}}
          search-node (assoc node :props [{:number "12345678"} {:number "87654321"}])]
      (t/are [expected-cypher entity return?]
          (= expected-cypher (sut/lookup-query entity return?))
        "MATCH (G__123) WHERE ID(G__123) = 4" (assoc node :id 4) false
        "MATCH (G__123) WHERE ID(G__123) = 4 RETURN G__123" (assoc node :id 4) true
        "MATCH (G__123:Label1:Label2 {a: '1', b: '2'})" node false
        "MATCH (G__123:Label1:Label2 {a: '1', b: '2'}) RETURN G__123" node true
        "MATCH (G__123:Label1:Label2) WHERE G__123.number = '12345678' OR G__123.number = '87654321'" search-node false
        "MATCH (G__123:Label1:Label2) WHERE G__123.number = '12345678' OR G__123.number = '87654321' RETURN G__123" search-node true))))

(t/deftest index-query
  (t/testing "Cypher to create/delete a index"
    (let [expected-cypher " INDEX ON Phone(number)"]
      (t/are [operation]
          (= (str operation expected-cypher) (sut/index-query operation :phone :number))
        "CREATE"
        "DROP"))))

(t/deftest create-relationship-query
  (let [next-gensym (atom 0)]
    (with-redefs [sut/generate-ref-id (fn [] (str "G__" (swap! next-gensym inc)))]
      (t/testing "Cypher to create a relationship between two nodes"
        (let [by-ref-rel {:ref-id "r"
                          :from {:ref-id "f"}
                          :to {:ref-id "l"}
                          :type :test-relation
                          :props {:a 1}}
              by-id-rel (assoc by-ref-rel :from {:id 1} :to {:id 2})
              by-lookup-rel (assoc by-ref-rel
                                   :from {:labels [:fragment :phone] :props {:b 2}}
                                   :to {:labels [:fragment :address] :props {:c "6"}})
              by-combined-rel (assoc by-ref-rel
                                     :from {:labels [:fragment :phone] :props #{{:b 2} {:b 5}}}
                                     :to {:ref-id "G__123"})]
          (t/are [expected-cypher rel-spec return?]
              (= expected-cypher (do (reset! next-gensym 0)
                                     (sut/create-relationship-query rel-spec return?)))
            "CREATE (f)-[r:TEST_RELATION {a: 1}]->(l)" by-ref-rel false
            "CREATE (f)-[r:TEST_RELATION {a: 1}]->(l) RETURN r" by-ref-rel true
            "MATCH (G__1) WHERE ID(G__1) = 1 MATCH (G__2) WHERE ID(G__2) = 2 CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__2)" by-id-rel false
            "MATCH (G__1) WHERE ID(G__1) = 1 MATCH (G__2) WHERE ID(G__2) = 2 CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__2) RETURN r" by-id-rel true
            "MATCH (G__1:Fragment:Phone {b: 2}) MATCH (G__2:Fragment:Address {c: '6'}) CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__2)" by-lookup-rel false
            "MATCH (G__1:Fragment:Phone {b: 2}) MATCH (G__2:Fragment:Address {c: '6'}) CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__2) RETURN r" by-lookup-rel true
            "MATCH (G__1:Fragment:Phone) WHERE G__1.b = 2 OR G__1.b = 5 CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__123)" by-combined-rel false
            "MATCH (G__1:Fragment:Phone) WHERE G__1.b = 2 OR G__1.b = 5 CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__123) RETURN r" by-combined-rel true))))))

(t/deftest modify-labels-query
  (t/testing "Cypher for Modifying labels on an entity"
    (t/are [operation]
        (= (str "MATCH (G__1) WHERE ID(G__1) = 1 " operation " G__1:Person:Director")
           (sut/modify-labels-query operation {:ref-id "G__1" :id 1} [:person :director]))
      "SET"
      "REMOVE")))

(t/deftest modify-properties-query
  (t/testing "Cypher for Modifying properties on an entity"
    (t/are [operation]
        (= (str "MATCH (G__1) WHERE ID(G__1) = 1 SET G__1 " operation " {a: 1, b: 2}")
           (sut/modify-properties-query operation {:ref-id "G__1" :id 1} {:a 1 :b 2}))
      "="
      "+=")))

(t/deftest delete-query
  (t/testing "Cypher for deleting an entity"
    (t/is (= "MATCH (G__1) WHERE ID(G__1) = 1 DELETE G__1" (sut/delete-query {:ref-id "G__1" :id 1})))))
