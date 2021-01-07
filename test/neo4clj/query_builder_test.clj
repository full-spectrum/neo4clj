(ns neo4clj.query-builder-test
  (:require [clojure.test :as t]
            [neo4clj.cypher :as cypher]
            [neo4clj.query-builder :as sut]))

(t/deftest create-node-query
  (t/testing "Cypher for creating a node with/without return"
    (let [basic-node {:ref-id "G__123"}
          node-labels (assoc basic-node :labels [:label1])
          node-props (assoc basic-node :props {:a "1"})
          node-complete (merge node-labels node-props)]
      (t/are [query node return?]
          (= query (sut/create-node-query node return?))
        "CREATE (G__123)" basic-node false
        "CREATE (G__123) RETURN G__123" basic-node true
        "CREATE (G__123:Label1)" node-labels false
        "CREATE (G__123:Label1) RETURN G__123" node-labels true
        "CREATE (G__123 {a: '1'})" node-props false
        "CREATE (G__123 {a: '1'}) RETURN G__123" node-props true
        "CREATE (G__123:Label1 {a: '1'})" node-complete false
        "CREATE (G__123:Label1 {a: '1'}) RETURN G__123" node-complete true))))

(t/deftest lookup-node
  (t/testing "Cypher for lookup node query"
    (let [node {:ref-id "G__123"
                :id 4
                :labels [:label1 :label2]
                :props {:a "1" :b "2"}}]
      (t/are [expected-cypher entity return?]
          (= expected-cypher (sut/lookup-node entity return?))
        "MATCH (G__123:Label2:Label1) WHERE ID(G__123) = 4" node false
        "MATCH (G__123:Label2:Label1) WHERE ID(G__123) = 4 RETURN G__123" (assoc node :id 4) true))))

(t/deftest lookup-rel
  (t/testing "Cypher for lookup relationship query"
    (let [rel {:ref-id "G__234" :id 4 :from {:ref-id "G__123"} :to {:ref-id "G__345"} :type :enemy :props {:a 2 :b 4}}]
      (t/are [expected-cypher entity return?]
          (= expected-cypher (sut/lookup-rel entity return?))
        "MATCH (G__123)-[G__234:ENEMY]->(G__345) WHERE ID(G__234) = 4" rel false
        "MATCH (G__123)-[G__234:ENEMY]->(G__345) WHERE ID(G__234) = 4 RETURN G__234" rel true))))


(t/deftest index-query
  (t/testing "Cypher to create/delete a index"
    (let [expected-cypher " INDEX ON :Phone(number)"]
      (t/are [operation]
          (= (str operation expected-cypher) (sut/index-query operation :phone [:number]))
        "CREATE"
        "DROP"))))

(t/deftest lookup-non-referred-node
  (t/testing "Cypher to lookup a node if it isn't allready reffered"
    (t/are [cypher node]
        (= cypher (sut/lookup-non-referred-node "n" node))
      "MATCH (n) " {:ref-id "G__123"}
      "MATCH (n) " {}
      "MATCH (n:Person) " {:labels [:person]}
      "MATCH (n) WHERE ID(n) = 12 " {:id 12})))

(t/deftest create-rel-query
  (let [next-gensym (atom 0)]
    (with-redefs [cypher/gen-ref-id (fn [] (str "G__" (swap! next-gensym inc)))]
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
                                     (sut/create-rel-query rel-spec return?)))
            "MATCH (f) MATCH (l) CREATE (f)-[r:TEST_RELATION {a: 1}]->(l)" by-ref-rel false
            "MATCH (f) MATCH (l) CREATE (f)-[r:TEST_RELATION {a: 1}]->(l) RETURN r" by-ref-rel true
            "MATCH (G__1) WHERE ID(G__1) = 1 MATCH (G__2) WHERE ID(G__2) = 2 CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__2)" by-id-rel false
            "MATCH (G__1) WHERE ID(G__1) = 1 MATCH (G__2) WHERE ID(G__2) = 2 CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__2) RETURN r" by-id-rel true
            "MATCH (G__1:Phone:Fragment {b: 2}) MATCH (G__2:Address:Fragment {c: '6'}) CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__2)" by-lookup-rel false
            "MATCH (G__1:Phone:Fragment {b: 2}) MATCH (G__2:Address:Fragment {c: '6'}) CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__2) RETURN r" by-lookup-rel true
            "MATCH (G__1:Phone:Fragment) WHERE ((G__1.b = 2) OR (G__1.b = 5)) MATCH (G__123) CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__123)" by-combined-rel false
            "MATCH (G__1:Phone:Fragment) WHERE ((G__1.b = 2) OR (G__1.b = 5)) MATCH (G__123) CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__123) RETURN r" by-combined-rel true))))))

(t/deftest node-reference
  (t/testing "Cypher to lookup or reference a already lookded up node"
    (t/are [expected-cypher-parts known-ref-ids node]
        (= expected-cypher-parts (sut/node-reference known-ref-ids node))
      ["(G__123:Person)" nil] #{} {:ref-id "G__123" :labels [:person]}
      ["(G__123 {firstName: 'Neo'})" nil] #{} {:ref-id "G__123" :props {:first-name "Neo"}}
      ["(G__123)" "ID(G__123) = 14"] #{} {:ref-id "G__123" :id 14}
      ["(G__123)" nil] #{"G__123"} {:ref-id "G__123" :labels [:person]}
      ["(G__123)" nil] #{"G__123"} {:ref-id "G__123" :props {:first-name "Neo"}}
      ["(G__123)" nil] #{"G__123"} {:ref-id "G__123" :id 14})))

(t/deftest non-existing-rel-query
  (t/testing "Cypher to create relationship not exists query"
    (t/are [expected-cypher rel known-ref-ids]
        (= expected-cypher (sut/non-existing-rel-query rel known-ref-ids))
      "NOT (G__1)-[:LIVING_AT]->(G__2)" {:from {:ref-id "G__1" :id 89} :to {:ref-id "G__2" :labels [:city]} :type :living-at} #{"G__1" "G__2"})))

(t/deftest modify-labels-query
  (t/testing "Cypher for Modifying labels on an entity"
    (t/are [operation]
        (= (str "MATCH (G__1) WHERE ID(G__1) = 1 " operation " G__1:Person:Director")
           (sut/modify-labels-query operation {:ref-id "G__1" :id 1} [:director :person]))
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
