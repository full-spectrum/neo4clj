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

(t/deftest create-graph-query
  (t/testing "Cypher to create a graph"
    (let [graph-map {:lookups [{:ref-id "n1" :labels [:person] :id 4}]
                     :nodes [{:ref-id "n2" :labels [:company]}]
                     :rels [{:ref-id "r1" :type :employee :from "n1" :to {:ref-id "n2"}}]
                     :returns ["n1" {:ref-id "n2"} "r1"]}]
      (t/are [expected-cypher graph-spec]
          (= expected-cypher (sut/create-graph-query graph-spec))
        "MATCH (n1:Person) WHERE ID(n1) = 4 CREATE (n1)-[r1:EMPLOYEE]->(n2:Company) RETURN n1, n2, r1" graph-map
        "MATCH (n1:Person) WHERE ID(n1) = 4 MATCH (n2:Company) CREATE (n1)-[r1:EMPLOYEE]->(n2) RETURN n1, n2, r1" (dissoc (update-in graph-map [:lookups] concat (:nodes graph-map)) :nodes)
        "CREATE (n1:Person)-[r1:EMPLOYEE]->(n2:Company) RETURN n1, n2, r1" (dissoc (update-in graph-map [:nodes] concat (:lookups graph-map)) :lookups)
        "MATCH (n1:Person) WHERE ID(n1) = 4 MATCH (n2) WHERE ID(n2) = 5 CREATE (n1)-[r1:EMPLOYEE]->(n2) RETURN n1, n2, r1" (dissoc (update-in graph-map [:lookups] conj {:ref-id "n2" :id 5}) :nodes)
        "MATCH (n1:Person) WHERE ID(n1) = 4 CREATE (n1)-[r1:EMPLOYEE]->(n2:Company) CREATE (n2)-[r2:EMPLOYES]->(n1) RETURN n1, n2, r1, r2" (update-in (update-in graph-map [:rels] conj {:ref-id "r2" :type :employes :from "n2" :to "n1"}) [:returns] conj "r2")))))

(t/deftest lookup-graph-query
  (t/testing "Cypher to lookup a graph"
    (let [node-entries {"n1" {:ref-id "n1" :labels [:person]}}
          relationships [{:ref-id "r1" :type :owner :from "n1" :to {:ref-id "n2" :labels [:person] :id 4}}]]
      (t/are [expected-cypher nodes rels]
          (= expected-cypher (sut/lookup-graph-query nodes rels))
        "MATCH (n1:Person)-[r1:OWNER]->(n2:Person) WHERE ID(n2) = 4" node-entries relationships
        "MATCH ()-[r1:OWNER]->()" node-entries [{:ref-id "r1" :type :owner :from nil}]
        "MATCH ()-[r1:OWNER]->()" node-entries [{:ref-id "r1" :type :owner :from nil :to {}}]
        "MATCH (n1:Person)-[r1:OWNER]->(n2:Person) WHERE ID(n2) = 4" (assoc node-entries "n3" {:ref-id "n3" :labels [:customer]}) relationships
        "MATCH (n1:Person)-[r1:OWNER]->(n2:Person)" (assoc node-entries "n2" {:ref-id "n2" :labels [:person]}) [(assoc (first relationships) :to "n2")]
        "MATCH (n1:Person)-[r1:OWNER]->(n2:Person) MATCH (n1)-[r2:EMPLOYEE]->(n3:Company) WHERE ID(n2) = 4 AND ID(n3) = 6" (assoc node-entries "n3" {:ref-id "n3" :labels [:company]:id 6}) (conj relationships {:ref-id "r2" :type :employee :from "n1" :to "n3"})
        "MATCH (n1:Person)-[r1:OWNER]->(n2:Person) MATCH (n3:Company) WHERE ID(n2) = 4 AND ID(n3) = 6 AND NOT (n1)-[:EMPLOYEE]->(n3)" (assoc node-entries "n3" {:ref-id "n3" :labels [:company]:id 6}) (conj relationships {:ref-id "r2" :type :employee :from "n1" :to "n3" :exists false})
        "MATCH (n1:Person) MATCH (n3:Company) WHERE ID(n3) = 6 AND NOT (n1)-[:EMPLOYEE]->(n3)" (assoc node-entries "n3" {:ref-id "n3" :labels [:company]:id 6}) [{:ref-id "r2" :type :employee :from "n1" :to "n3" :exists false}]))))

(t/deftest get-graph-query
  (t/testing "Cypher to fetch a graph"
    (let [graph-map {:nodes [{:ref-id "n1" :labels [:company]}]
                     :rels [{:ref-id "r1" :type :owner :from "n1" :to {:ref-id "n2" :labels [:person] :id 4}}]
                     :returns ["n1" {:ref-id "n2"} "r1"]}]
      (t/are [expected-cypher graph-spec]
          (= expected-cypher (sut/get-graph-query graph-spec))
        "MATCH (n1:Company)-[r1:OWNER]->(n2:Person) WHERE ID(n2) = 4 RETURN n1, n2, r1" graph-map))))

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

(t/deftest delete-node
  (t/testing "Cypher for deleting a node"
    (t/is (= "MATCH (G__1) WHERE ID(G__1) = 1 DELETE G__1" (sut/delete-node {:ref-id "G__1" :id 1})))))

(t/deftest delete-rel
  (t/testing "Cypher for deleting a relationship"
    (t/is (= "MATCH ()-[G__1]->() WHERE ID(G__1) = 1 DELETE G__1" (sut/delete-rel {:ref-id "G__1" :id 1})))))
