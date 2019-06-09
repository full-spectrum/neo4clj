(ns neo4clj.convert-test
  (:require [clojure.test :as t]
            [java-time :as time]
            [neo4clj.convert :as sut]))

(def neo4j-node
  "Mock object to represent a Neo4J Node"
  (proxy [org.neo4j.driver.v1.types.Node] []
    (id [] 1)
    (labels [] ["Person"])
    (asMap [] {"firstName" "Neo"
               "lastName" "Anderson"})))

(def neo4j-relationship
  "Mock object to represent a Neo4J Relationship"
  (proxy [org.neo4j.driver.v1.types.Relationship] []
    (id [] 4)
    (type [] "EMPLOYEE")
    (startNodeId [] 4)
    (endNodeId [] 11)
    (asMap [] {"hiredAt" 2008
               "position" "Technician"})))

(def neo4j-record
  "Mock object to represent a Neo4J Record containing tow Nodes"
  (proxy [org.neo4j.driver.v1.Record] []
    (asMap [] {"n" neo4j-node "r" neo4j-relationship})))

(def statement-result
  "Atom used for the iterator functionality of StatementResult below"
  (atom nil))

(def neo4j-statement-result
  "Mock object to represent a Neo4J StatementResult"
  (proxy [org.neo4j.driver.v1.StatementResult] []
    (hasNext [] (not (empty? @statement-result)))
    (next [] (let [v (first @statement-result)]
               (swap! statement-result pop)
               v))))

(t/deftest property->value
  (t/testing "Convert Neo4j property to its Clojure equivalent"
    (t/are [clj-prop neo4j-prop]
        (= clj-prop (sut/property->value neo4j-prop))
      nil nil
      12 12
      "Neo" "Neo"
      true true
      false false
      (time/instant "2018-04-28T12:53:11Z") "2018-04-28T12:53:11Z")))

(t/deftest neo4j-entity-basics->clj
  (t/testing "Convert basic Neo4j entity to a Clojure map"
    (t/are [clj-map neo4j-entity]
        (= clj-map (sut/neo4j-entity-basics->clj neo4j-entity))
      {:id 1 :props {:first-name "Neo" :last-name "Anderson"}} neo4j-node
      {:id 4 :props {:hired-at 2008 :position "Technician"}} neo4j-relationship)))

(t/deftest neo4j->clj
  (t/testing "Convert a Neo4j entity to its Clojure equivalent"
    (t/are [clj-representation neo4j-entity]
        (do (reset! statement-result [neo4j-record])
            (= clj-representation (sut/neo4j->clj neo4j-entity)))
      {:id 1 :labels [:person] :props {:first-name "Neo" :last-name "Anderson"}} neo4j-node
      {:id 4 :type :employee :start-id 4 :end-id 11 :props {:hired-at 2008 :position "Technician"}} neo4j-relationship
      '({"n" {:id 1 :labels [:person] :props {:first-name "Neo" :last-name "Anderson"}}
         "r" {:id 4 :type :employee :start-id 4 :end-id 11 :props {:hired-at 2008 :position "Technician"}}}) neo4j-statement-result)))

(t/deftest clj-value->neo4j-value
  (t/testing "Convert Clojure property to its Neo4j equivalent"
    (t/are [neo4j-prop clj-prop]
        (= neo4j-prop (sut/clj-value->neo4j-value clj-prop))
      "NULL" nil
      12 12
      "'Neo'" "Neo"
      "TRUE" true
      "FALSE" false
      "'2018-04-28T12:53:11Z'" (time/instant "2018-04-28T12:53:11Z"))))

(t/deftest hash-map->properties
  (t/testing "Convert Clojure properties map to a sanitized Neo4j properties map"
    (t/are [neo4j-props clj-props]
        (= neo4j-props (sut/hash-map->properties clj-props))
      {"age" 46} {:age 46}
      {"firstName" "'Neo'" "lastName" "'Anderson'"} {:first-name "Neo" :last_name "Anderson"})))

(t/deftest clj-rel->neo4j
  (t/testing "Convert a Clojure relationship to a sanitized Neo4j relationship representation"
    (t/are [neo4j-rel clj-rel]
        (= neo4j-rel (sut/clj-rel->neo4j clj-rel))
      {:ref-id "r" :type nil :from {:ref-id "p"} :to {:ref-id "c"} :props nil} {:ref-id "r" :from {:ref-id "p"} :to {:ref-id "c"}}
      {:ref-id "r" :type "EMPLOYEE" :from {:ref-id "p"} :to {:ref-id "c"} :props nil} {:ref-id "r" :type :employee :from {:ref-id "p"} :to {:ref-id "c"}}
      {:ref-id "r" :id 4 :type "EMPLOYEE" :from {:ref-id "p"} :to {:ref-id "c"} :props {"hiredAt" 2008 "position" "'Technician'"}} {:ref-id "r"  :id 4 :type :employee :from {:ref-id "p"} :to {:ref-id "c"} :props {:hired-at 2008 :position "Technician"}})))
