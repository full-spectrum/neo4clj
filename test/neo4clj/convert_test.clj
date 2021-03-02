(ns neo4clj.convert-test
  (:require [clojure.test :as t]
            [java-time :as time]
            [neo4clj.convert :as sut]))

(def neo4j-node
  "Mock object to represent a Neo4J Node"
  (proxy [org.neo4j.driver.types.Node] []
    (id [] 1)
    (labels [] ["Person"])
    (asMap [] {"firstName" "Neo"
               "lastName" "Anderson"})))

(def neo4j-complex-node
  "Mock object to represent a Neo4J Node with many value types"
  (proxy [org.neo4j.driver.types.Node] []
    (id [] 2)
    (labels [] ["Person"])
    (asMap [] {"firstName" "Neo"
               "lastName" "Anderson"
               "occupation" nil
               "age" 45
               "married" false
               "have-girlfriend" true
               "friends" ["Morpheus" "Trinity"]
               "escaped" (time/with-zone (time/zoned-date-time 1999 3 31 0 0 0) "UTC")})))

(def neo4j-relationship
  "Mock object to represent a Neo4J Relationship"
  (proxy [org.neo4j.driver.types.Relationship] []
    (id [] 4)
    (type [] "EMPLOYEE")
    (startNodeId [] 4)
    (endNodeId [] 11)
    (asMap [] {"hiredAt" 2008
               "position" "Technician"})))

(def neo4j-record
  "Mock object to represent a Neo4J Record containing two Nodes"
  (proxy [org.neo4j.driver.Record] []
    (asMap [] {"n" neo4j-node "r" neo4j-relationship "t.value" 45})))

(def statement-result
  "Atom used for the iterator functionality of StatementResult below"
  (atom nil))

(def neo4j-statement-result
  "Mock object to represent a Neo4J StatementResult"
  (proxy [org.neo4j.driver.Result] []
    (hasNext [] (not (empty? @statement-result)))
    (next [] (let [v (first @statement-result)]
               (swap! statement-result pop)
               v))))

(t/deftest neo4j-entity-basics->clj
  (t/testing "Convert basic Neo4j entity to a Clojure map"
    (t/are [clj-map neo4j-entity]
        (= clj-map (sut/neo4j-entity-basics->clj neo4j-entity))
      {:id 1 :props {:first-name "Neo" :last-name "Anderson"}} neo4j-node
      {:id 4 :props {:hired-at 2008 :position "Technician"}} neo4j-relationship
      {:id 2 :props {:first-name "Neo"
                     :last-name "Anderson"
                     :occupation nil
                     :age 45
                     :married false
                     :have-girlfriend true
                     :friends ["Morpheus" "Trinity"]
                     :escaped (java-time/with-zone (java-time/zoned-date-time 1999 3 31 0 0 0) "UTC")}} neo4j-complex-node)))

(t/deftest neo4j->clj
  (t/testing "Convert a Neo4j entity to its Clojure equivalent"
    (t/are [clj-representation neo4j-entity]
        (do (reset! statement-result [neo4j-record])
            (= clj-representation (sut/neo4j->clj neo4j-entity)))
      "Anderson" (java.lang.String. "Anderson")
      123456789 (java.lang.Long. 123456789)
      {"a" 1 "b" 2} (java.util.Collections/unmodifiableMap (doto (java.util.Hashtable. )
                                                             (.put "a" 1)
                                                             (.put "b" 2)))
      {:id 1 :labels [:person] :props {:first-name "Neo" :last-name "Anderson"}} neo4j-node
      {:id 4 :type :employee :start-id 4 :end-id 11 :props {:hired-at 2008 :position "Technician"}} neo4j-relationship
      '({"n" {:id 1 :ref-id "n" :labels [:person] :props {:first-name "Neo" :last-name "Anderson"}}
         "r" {:id 4 :ref-id "r" :type :employee :start-id 4 :end-id 11 :props {:hired-at 2008 :position "Technician"}}
         "t.value" 45}) neo4j-statement-result)))

(t/deftest clj-value->neo4j-value
  (t/testing "Convert Clojure property to its Neo4j equivalent"
    (t/are [neo4j-prop clj-prop]
        (= neo4j-prop (sut/clj-value->neo4j-value clj-prop))
      "NULL" nil
      12 12
      "'Neo'" "Neo"
      "TRUE" true
      "FALSE" false
      "'key'" :key
      "['test', 'something', 'else']" ["test" "something" "else"]
      "['else', 'something', 'test']" #{"test" "something" "else"}
      "datetime(\"2018-04-28T12:53:11Z\")" (time/instant "2018-04-28T12:53:11Z"))))

(t/deftest hash-map->properties
  (t/testing "Convert Clojure properties map to a sanitized Neo4j properties map"
    (t/are [neo4j-props clj-props]
        (= neo4j-props (sut/hash-map->properties clj-props))
      {"age" 46} {:age 46}
      {"firstName" "'Neo'" "lastName" "'Anderson'"} {:first-name "Neo" :last_name "Anderson"})))

(t/deftest clj-relationship->neo4j
  (t/testing "Convert a Clojure relationship to a sanitized Neo4j relationship representation"
    (t/are [neo4j-rel clj-rel]
        (= neo4j-rel (sut/clj-relationship->neo4j clj-rel))
      {:ref-id "r" :type nil :from {:ref-id "p"} :to {:ref-id "c"} :props nil} {:ref-id "r" :from {:ref-id "p"} :to {:ref-id "c"}}
      {:ref-id "r" :type "EMPLOYEE" :from {:ref-id "p"} :to {:ref-id "c"} :props nil} {:ref-id "r" :type :employee :from {:ref-id "p"} :to {:ref-id "c"}}
      {:ref-id "r" :id 4 :type "EMPLOYEE" :from {:ref-id "p"} :to {:ref-id "c"} :props {"hiredAt" 2008 "position" "'Technician'"}} {:ref-id "r"  :id 4 :type :employee :from {:ref-id "p"} :to {:ref-id "c"} :props {:hired-at 2008 :position "Technician"}})))

(t/deftest clj-parameter->neo4j
  (t/testing "Convert a collection of Clojure values into Neo4J parameter friendly values"
    (t/are [neo4j-rel clj-rel]
        (= neo4j-rel (sut/clj-parameter->neo4j clj-rel))
      (java-time/with-zone (java-time/zoned-date-time 2018 4 28 12 53 11) "UTC") (time/instant "2018-04-28T12:53:11Z")
      45 45
      "keyword" :keyword
      true true
      false false
      nil nil
      [1 2 3] [1 2 3])))

(t/deftest clj-parameters->neo4j
  (t/testing "Convert a collection of Clojure values into Neo4J parameter friendly values"
    (t/are [neo4j-rel clj-rel]
        (= neo4j-rel (sut/clj-parameters->neo4j clj-rel))
      (org.neo4j.driver.Values/parameters
       (into-array Object ["date" (java-time/with-zone (java-time/zoned-date-time 2018 4 28 12 53 11) "UTC") "number" 45 "title" "keyword" "true" true "false" false "non-existent" nil "collection" [1 2 3]]))
      {:date (time/instant "2018-04-28T12:53:11Z") :number 45 "title" :keyword "true" true "false" false :non-existent nil "collection" [1 2 3]})))
