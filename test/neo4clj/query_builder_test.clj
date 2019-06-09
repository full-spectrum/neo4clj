(ns neo4clj.query-builder-test
  (:require [clojure.test :as t]
            [neo4clj.cypher :as cypher]
            [neo4clj.query-builder :as sut]))

(t/deftest node-representation
  (t/testing "Cypher representation of a node including where parts"
    (t/are [cypher-parts node]
        (= cypher-parts (sut/node-representation node))
      ["(n)" nil] {:ref-id "n"}
      ["(p:Person)" nil] {:ref-id "p" :labels [:person]}
      ["(c:Person:Customer)" nil] {:ref-id "c" :labels [:customer :person]}
      ["(c:Person:Customer {firstName: 'Neo', lastName: 'Anderson'})" nil] {:ref-id "c"
                                                                                :labels [:customer :person]
                                                                                :props {:first-name "Neo"
                                                                                        :last_name "Anderson"}}
      ["(c:Person:Customer)" "c.firstName = 'Neo' OR c.lastName = 'Anderson'"] {:ref-id "c"
                                                                                :labels [:customer :person]
                                                                                :props [{:first-name "Neo"}
                                                                                        {:last_name "Anderson"}]}
      ["(c)" "ID(c) = 12"] {:ref-id "c" :labels [:customer :person] :id 12})))

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
        "MATCH (G__123:Label2:Label1 {a: '1', b: '2'})" node false
        "MATCH (G__123:Label2:Label1 {a: '1', b: '2'}) RETURN G__123" node true
        "MATCH (G__123:Label2:Label1) WHERE G__123.number = '12345678' OR G__123.number = '87654321'" search-node false
        "MATCH (G__123:Label2:Label1) WHERE G__123.number = '12345678' OR G__123.number = '87654321' RETURN G__123" search-node true))))

(t/deftest index-query
  (t/testing "Cypher to create/delete a index"
    (let [expected-cypher " INDEX ON Phone(number)"]
      (t/are [operation]
          (= (str operation expected-cypher) (sut/index-query operation :phone :number))
        "CREATE"
        "DROP"))))

(t/deftest lookup-non-referred-node
  (t/testing "Cypher to lookup a node if it isn't allready reffered"
    (t/are [cypher node]
        (= cypher (sut/lookup-non-referred-node "n" node))
      nil {:ref-id "G__123"}
      "MATCH (n) " {}
      "MATCH (n:Person) " {:labels [:person]}
      "MATCH (n) WHERE ID(n) = 12 " {:id 12})))

(t/deftest relationship-representation
  (t/testing "Cypher representation of a relationship"
    (t/are [cypher rel]
        (= cypher (sut/relationship-representation "(p:Person)" "(c:Company)" rel))
      "(p:Person)-[]->(c:Company)" {}
      "(p:Person)-[r]->(c:Company)" {:ref-id "r"}
      "(p:Person)-[:EMPLOYEE]->(c:Company)" {:type :employee}
      "(p:Person)-[r:FORMER_EMPLOYEE]->(c:Company)" {:ref-id "r" :type :former-employee}
      "(p:Person)-[r {hiredAt: 2008}]->(c:Company)" {:ref-id "r" :props {:hired-at 2008}}
      "(p:Person)-[r:EMPLOYEE {hiredAt: 2008}]->(c:Company)" {:ref-id "r" :type :employee :props {:hired-at 2008}})))

(t/deftest create-relationship-query
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
                                     (sut/create-relationship-query rel-spec return?)))
            "CREATE (f)-[r:TEST_RELATION {a: 1}]->(l)" by-ref-rel false
            "CREATE (f)-[r:TEST_RELATION {a: 1}]->(l) RETURN r" by-ref-rel true
            "MATCH (G__1) WHERE ID(G__1) = 1 MATCH (G__2) WHERE ID(G__2) = 2 CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__2)" by-id-rel false
            "MATCH (G__1) WHERE ID(G__1) = 1 MATCH (G__2) WHERE ID(G__2) = 2 CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__2) RETURN r" by-id-rel true
            "MATCH (G__1:Phone:Fragment {b: 2}) MATCH (G__2:Address:Fragment {c: '6'}) CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__2)" by-lookup-rel false
            "MATCH (G__1:Phone:Fragment {b: 2}) MATCH (G__2:Address:Fragment {c: '6'}) CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__2) RETURN r" by-lookup-rel true
            "MATCH (G__1:Phone:Fragment) WHERE G__1.b = 2 OR G__1.b = 5 CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__123)" by-combined-rel false
            "MATCH (G__1:Phone:Fragment) WHERE G__1.b = 2 OR G__1.b = 5 CREATE (G__1)-[r:TEST_RELATION {a: 1}]->(G__123) RETURN r" by-combined-rel true))))))

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
