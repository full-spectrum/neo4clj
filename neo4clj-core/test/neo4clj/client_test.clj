(ns neo4clj.client-test
  (:require [clojure.test :as t]
            [neo4clj.client :as sut]
            [neo4clj.test-utils :as test-utils]))

(t/deftest with-session
  ;; This test also tests the create-session and execute! function
  (t/testing "Run cyphers in a Neo4j session"
    (test-utils/with-db [conn {:initial-data ["CREATE (n:TestNode) RETURN n"]}]
      (t/is (= [{"n" {:labels [:test-node] :id 0 :ref-id "n" :props {}}}]
               (sut/with-session conn session
                 (sut/execute! session "MATCH (n:TestNode) RETURN n")))))))

(t/deftest with-transaction
  ;; This test also tests the begin-transaction, commit!, rollback and execute!
  (t/testing "Run cypher in a transaction"
    (test-utils/with-db [conn {}]
      (t/is (= [{"n" {:labels [:test-node] :id 0 :ref-id "n" :props {}}}]
               (sut/with-transaction conn transaction
                 (sut/execute! transaction "CREATE (n:TestNode) RETURN n")
                 (sut/execute! transaction "MATCH (n:TestNode) RETURN n"))))))
  (t/testing "Run cypher in a transaction with rollback on exception"
    (test-utils/with-db [conn {}]
      (t/is (empty? (do (try
                          (sut/with-transaction conn transaction
                            (sut/execute! transaction "CREATE (n:TestNode) RETURN n")
                            (throw (Exception. "Test Exception")))
                          (catch Exception e "Caught exception!"))
                        (sut/execute! conn "MATCH (n:TestNode) RETURN n")))))))

(t/deftest execute-read
  ;; This test also tests the with-read function
  (t/testing "Run read cypher in a Neo4j read-only connection"
    (test-utils/with-db [conn {:initial-data ["CREATE (n:TestNode) RETURN n"]}]
      (t/is (= [{"n" {:labels [:test-node] :id 0 :ref-id "n" :props {}}}]
               (sut/execute-read conn "MATCH (n:TestNode) RETURN n")))))
  (t/testing "Run write cypher in a Neo4j read-only connection"
    (test-utils/with-db [conn {}]
      (t/is (thrown? java.lang.Exception
                     (sut/execute-read conn "CREATE (n:TestNode) RETURN n"))))))

(t/deftest execute-write!
  ;; This test also tests the with-write-conn function
  (t/testing "Run write cypher without params in a Neo4j write connection"
    (test-utils/with-db [conn {}]
      (t/is (= [{"n" {:labels [:test-node] :id 0 :ref-id "n" :props {}}}]
               (sut/execute-write! conn "CREATE (n:TestNode) RETURN n")))))
  (t/testing "Run write cypher with params in a Neo4j write connection"
    (test-utils/with-db [conn {}]
      (t/is (= [{"n" {:labels [:test-node] :id 0 :ref-id "n" :props {:name "Neo"}}}]
               (sut/execute-write! conn "CREATE (n:TestNode {name: $name}) RETURN n" {:name "Neo"}))))))

(t/deftest create-index!
  (t/testing "Create index on a Neo4j node property"
    (test-utils/with-db [conn {:initial-data ["CREATE (n:TestNode {name: 'Neo'}) RETURN n"]}]
      (t/is (= [{"properties" ["name"] "labelsOrTypes" ["TestNode"]}]
               (map #(select-keys % ["properties" "labelsOrTypes"])
                    (filter #(= (get % "labelsOrTypes") ["TestNode"])
                            (do (sut/create-index! conn :test-node [:name])
                                (sut/execute! conn "CALL db.indexes")))))))))

(t/deftest drop-index!
  (t/testing "Drop index on a Neo4j node property"
    (test-utils/with-db [conn {:initial-data ["CREATE INDEX ON :TestNode(name)"]}]
      (t/is (empty? (filter #(= (get % "labelsOrTypes") ["TestNode"])
                            (do (sut/drop-index! conn :test-node [:name])
                                (sut/execute! conn "CALL db.indexes"))))))))

(t/deftest create-node!
  (t/testing "Create a Neo4j node though builder"
    (test-utils/with-db [conn {}]
      (t/is (= {:labels [:test-node] :id 0 :ref-id "n" :props {:name "Neo"}}
               (sut/create-node! conn {:ref-id "n" :labels [:test-node] :props {:name "Neo"}}))))))

(t/deftest create-rel!
  (t/testing "Create a Neo4j relation though builder"
    (test-utils/with-db [conn {:initial-data ["CREATE (n:TestNode {name: 'Neo'})"
                                              "CREATE (n:TestNode {name: 'Trinity'})"]}]
      (t/is (= {:end-id 1 :type :friends :start-id 0 :id 0 :ref-id "r" :props {:since "1999"}}
               (sut/create-rel! conn {:ref-id "r"
                                      :type :friends
                                      :from {:labels [:test-node] :props {:name "Neo"}}
                                      :to {:labels [:test-node] :props {:name "Trinity"}}
                                      :props {:since "1999"}}))))))

(t/deftest find-node
  (test-utils/with-db [conn {:initial-data ["CREATE (n:TestNode {name: 'Neo'})"]}]
    (t/testing "Find a Neo4j node"
      (t/is (= {:labels [:test-node] :id 0 :ref-id "n" :props {:name "Neo"}}
               (sut/find-node conn {:ref-id "n" :labels [:test-node] :props {:name "Neo"}}))))
    (t/testing "Try to find a non-existing Neo4j node"
      (t/is (nil? (sut/find-node conn {:ref-id "n" :labels [:test-node] :props {:name "Trinity"}}))))))

(t/deftest find-nodes
  (t/testing "Find collection of Neo4j nodes"
    (test-utils/with-db [conn {:initial-data ["CREATE (n:TestNode {name: 'Neo'})"
                                              "CREATE (n:TestNode {name: 'Trinity'})"
                                              "CREATE (n:TestNode {name: 'Morpheus'})"]}]
      (t/is (= [{:labels [:test-node] :id 0 :ref-id "n" :props {:name "Neo"}}
                {:labels [:test-node] :id 1 :ref-id "n" :props {:name "Trinity"}}]
               (sut/find-nodes conn {:ref-id "n" :labels [:test-node] :props [{:name "Neo"} {:name "Trinity"}]}))))))

(t/deftest find-rel
  (test-utils/with-db [conn {:initial-data ["CREATE (:TestNode {name: 'Neo'})-[:FRIENDS {since: 1999}]->(:TestNode {name: 'Trinity'})"]}]
    (t/testing "Find a Neo4j relation"
      (t/is (= {:end-id 1 :type :friends :start-id 0 :id 0 :ref-id "r" :props {:since 1999}}
               (sut/find-rel conn {:ref-id "r" :type :friends}))))
    (t/testing "Try to find a non-existing Neo4j relation"
      (t/is (nil? (sut/find-rel conn {:ref-id "r" :type :enemies}))))))

(t/deftest find-rels
  (t/testing "Find collection of Neo4j nodes"
    (test-utils/with-db [conn {:initial-data ["CREATE (:TestNode {name: 'Neo'})-[:FRIENDS {since: 1999}]->(:TestNode {name: 'Trinity'})<-[:FRIENDS {since: 1999}]-(:TestNode {name: 'Morpheus'})"]}]
      (t/is (= [{:end-id 1 :type :friends :start-id 0 :id 0 :ref-id "r" :props {:since 1999}}
                {:end-id 1 :type :friends :start-id 2 :id 1 :ref-id "r" :props {:since 1999}}]
               (sut/find-rels conn {:ref-id "r" :type :friends}))))))

(t/deftest create-graph!
  (t/testing "Create Neo4j nodes and relationships though builder"
    (test-utils/with-db [conn {}]
      (t/is (= [{"n1" {:labels [:test-node] :id 0 :ref-id "n1" :props {:name "Neo"}}
                 "n2" {:labels [:test-node] :id 1 :ref-id "n2" :props {:name "Trinity"}}
                 "r" {:end-id 1 :type :friends :start-id 0 :id 0 :ref-id "r" :props {:since "1999"}}}]
               (sut/create-graph! conn {:nodes [{:ref-id "n1" :labels [:test-node] :props {:name "Neo"}}
                                                {:ref-id "n2" :labels [:test-node] :props {:name "Trinity"}}]
                                        :rels [{:ref-id "r"
                                                :type :friends
                                                :from {:ref-id "n1"}
                                                :to {:ref-id "n2"}
                                                :props {:since "1999"}}]
                                        :returns ["n1" "n2" "r"]}))))))

(t/deftest get-graph
  (t/testing "Get Neo4j nodes and relationships"
    (test-utils/with-db [conn {:initial-data ["CREATE (:TestNode {name: 'Neo'})-[:FRIENDS {since: 1999}]->(:TestNode {name: 'Trinity'})<-[:FRIENDS {since: 1999}]-(:TestNode {name: 'Morpheus'})"]}]
      (t/is (= [{"n1" {:labels [:test-node] :id 0 :ref-id "n1" :props {:name "Neo"}}
                 "n2" {:labels [:test-node] :id 1 :ref-id "n2" :props {:name "Trinity"}}
                 "r" {:end-id 1 :type :friends :start-id 0 :id 0 :ref-id "r" :props {:since 1999}}}]
               (sut/get-graph conn {:nodes [{:ref-id "n1" :labels [:test-node] :props {:name "Neo"}}
                                            {:ref-id "n2" :labels [:test-node] :props {:name "Trinity"}}]
                                    :rels [{:ref-id "r"
                                            :type :friends
                                            :from "n1"
                                            :to {:ref-id "n2"}}]
                                    :returns ["n1" "n2" "r"]}))))))

(t/deftest add-labels!
  (t/testing "Add a collection of labels to the found Neo4j nodes"
    (test-utils/with-db [conn {:initial-data ["CREATE (n:TestNode {name: 'Neo'})"
                                              "CREATE (n:TestNode {name: 'Trinity'})"]}]
      (t/is (= [{"n" {:labels [:test-node :hero :character] :id 0 :ref-id "n" :props {:name "Neo"}}}
                {"n" {:labels [:test-node] :id 1 :ref-id "n" :props {:name "Trinity"}}}]
               (do (sut/add-labels! conn {:ref-id "n" :labels [:test-node] :props {:name "Neo"}} [:character :hero])
                   (sut/execute! conn "MATCH (n:TestNode) RETURN n")))))))

(t/deftest remove-labels!
  (t/testing "Remove a collection of labels to the found Neo4j nodes"
    (test-utils/with-db [conn {:initial-data ["CREATE (n:TestNode:Character:Hero {name: 'Neo'})"
                                              "CREATE (n:TestNode:Character:Hero {name: 'Trinity'})"]}]
      (t/is (= [{"n" {:labels [:test-node] :id 0 :ref-id "n" :props {:name "Neo"}}}
                {"n" {:labels [:test-node] :id 1 :ref-id "n" :props {:name "Trinity"}}}]
               (do (sut/remove-labels! conn {:ref-id "n" :labels [:test-node]} [:character :hero])
                   (sut/execute! conn "MATCH (n:TestNode) RETURN n")))))))

(t/deftest update-props!
  (t/testing "Updating properties on found Neo4j nodes"
    (test-utils/with-db [conn {:initial-data ["CREATE (n:TestNode {firstName: 'Neo', lastName: 'Jensen'})"
                                              "CREATE (n:TestNode {firstName: 'Trinity'})"]}]
      (t/is (= [{"n" {:labels [:test-node] :id 0 :ref-id "n" :props {:first-name "Neo" :last-name "Anderson" :age 45}}}
                {"n" {:labels [:test-node] :id 1 :ref-id "n" :props {:first-name "Trinity"}}}]
               (do (sut/update-props! conn {:ref-id "n" :labels [:test-node] :props {:first-name "Neo"}}
                                      {:last-name "Anderson" :age 45})
                   (sut/execute! conn "MATCH (n:TestNode) RETURN n")))))))

(t/deftest replace-props!
  (t/testing "Replace properties on found Neo4j nodes"
    (test-utils/with-db [conn {:initial-data ["CREATE (n:TestNode {firstName: 'Kim'})"
                                              "CREATE (n:TestNode {firstName: 'Trinity'})"]}]
      (t/is (= [{"n" {:labels [:test-node] :id 0 :ref-id "n" :props {:last-name "Anderson" :age 45}}}
                {"n" {:labels [:test-node] :id 1 :ref-id "n" :props {:first-name "Trinity"}}}]
               (do (sut/replace-props! conn {:ref-id "n" :labels [:test-node] :props {:first-name "Kim"}}
                                       {:last-name "Anderson" :age 45})
                   (sut/execute! conn "MATCH (n:TestNode) RETURN n")))))))

(t/deftest delete-node!
  (t/testing "Delete found Neo4j nodes"
    (test-utils/with-db [conn {:initial-data ["CREATE (n:TestNode {firstName: 'Neo'})"
                                              "CREATE (n:TestNode {firstName: 'Trinity'})"]}]
      (t/is (= [{"n" {:labels [:test-node] :id 1 :ref-id "n" :props {:first-name "Trinity"}}}]
               (do (sut/delete-node! conn {:ref-id "n" :labels [:test-node] :props {:first-name "Neo"}})
                   (sut/execute! conn "MATCH (n:TestNode) RETURN n")))))))

(t/deftest delete-rel!
  (t/testing "Delete found Neo4j relationships"
    (test-utils/with-db [conn {:initial-data ["CREATE (:TestNode {name: 'Neo'})-[:FRIENDS {since: 1999}]->(:TestNode {name: 'Trinity'})<-[:FRIENDS {since: 1999}]-(:TestNode {name: 'Morpheus'})"]}]
      (t/is (= [{"r" {:end-id 1 :type :friends :start-id 2 :id 1 :ref-id "r" :props {:since 1999}}}]
               (do (sut/delete-rel! conn {:ref-id "r" :type :friends :from {:ref-id "n1" :id 0} :to {:ref-id "n2" :id 1}})
                   (sut/execute! conn "MATCH ()-[r]-() RETURN DISTINCT r")))))))

(t/deftest create-query!
  ;; Tests the creation of an fn to execute later
  (test-utils/with-db [conn {:initial-data ["CREATE (:TestNode {name: 'Neo'})"
                                            "CREATE (:TestNode {name: 'Trinity'})"]}]
    (t/testing "Test create cypher fn without params"
      (t/is (= [{"n" {:labels [:test-node] :id 0 :ref-id "n" :props {:name "Neo"}}}
                {"n" {:labels [:test-node] :id 1 :ref-id "n" :props {:name "Trinity"}}}]
               ((sut/create-query "MATCH (n:TestNode) RETURN n") conn))))
    (t/testing "Test create cypher fn with params"
      (t/is (= [{"n" {:labels [:test-node] :id 0 :ref-id "n" :props {:name "Neo"}}}]
               ((sut/create-query "MATCH (n:TestNode {name: $name}) RETURN n") conn {:name "Neo"}))))))
