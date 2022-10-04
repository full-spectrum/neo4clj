(ns user
  (:require [neo4clj.client :as client]
            [criterium.core :as crit]))

#_(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(defn test-basic-node-logic
  [conn]
  (let [node (client/create-node! conn {:ref-id "n"
                                        :labels [:person]
                                        :props {:first-name "Neo" :last-name "Anderson"}})]
    (println "Nodes after creation:" (count (client/find-nodes conn {:ref-id "n" :labels [:person]})) "- Expected 1")
    (client/delete-node! conn {:id (:id node)})
    (println "Nodes after delete:" (count (client/find-nodes conn {:ref-id "n" :labels [:person]}))  "- Expected 0")))

(defn test-basic-relationship-logic
  [conn]
  (let [node-2 (client/with-write-conn conn tx
                 (client/create-node! tx {:ref-id "n1"
                                          :labels [:person]
                                          :props {:first-name "Neo" :last-name "Anderson"}})
                 (client/create-node! tx {:ref-id "n2"
                                          :labels [:person]
                                          :props {:first-name "Agent" :last-name "Smith"}}))]
    (println "Nodes after creation:"
             (count (client/with-read-only-conn conn tx
                      (client/find-nodes tx {:ref-id "n" :labels [:person]})))
             "- Expected 2")
    (let [rel (client/create-rel! conn {:ref-id "r" :type :enemy :props {:since "Forever"} :from {:ref-id "n1" :props {:first-name "Neo"}} :to node-2})]
      (println "Relationships after creation:"
               (count (client/with-read-only-conn conn tx
                        (client/find-rels tx {:ref-id "r" :type :enemy})))
                "- Expected 1")
      (client/delete-rel! conn rel)
      (println "Relationships after delete:"
               (count (client/with-read-only-conn conn tx
                        (client/find-rels tx {:ref-id "r" :type :enemy})))
                "- Expected 0")
      (client/delete-node! conn {:labels [:person]})
      (println "Nodes after delete:"
               (count (client/with-read-only-conn conn tx
                        (client/find-nodes tx {:ref-id "n" :labels [:person]})))
                "- Expected 0"))))

(defn test-write-exception
  [conn]
  (println "Nodes before creation:"
           (count (client/with-read-only-conn conn tx
                    (client/find-nodes tx {:ref-id "n"})))
            "- Expected 0")
  (try
    (client/with-write-conn conn tx
      (client/create-node! tx {:ref-id "n1"
                               :labels [:person]
                               :props {:first-name "Neo" :last-name "Anderson"}})
      (throw (Exception. "Test"))
      (client/create-node! tx {:ref-id "n2"
                               :labels [:person]
                               :props {:first-name "Agent" :last-name "Smith"}}))
    (catch Exception e (str "Nothing")))
  (println "Nodes after exception:"
           (count (client/with-read-only-conn conn tx
                    (client/find-nodes tx {:ref-id "n"})))
            "- Expected 0"))

(defn test-transaction
  [conn]
  (println "Nodes before creation:"
           (count (client/with-read-only-conn conn tx
                    (client/find-nodes tx {:ref-id "n"})))
            "- Expected 0")
  (client/with-transaction conn tx
    (client/create-node! tx {:ref-id "n1"
                             :labels [:person]
                             :props {:first-name "Neo" :last-name "Anderson"}}))
  (println "Nodes after transaction:"
           (count (client/with-read-only-conn conn tx
                    (client/find-nodes tx {:ref-id "n"})))
            "- Expected 1")
  (client/delete-node! conn {:ref-id "n"})
  (println "Nodes after delete:"
           (count (client/with-read-only-conn conn tx
                    (client/find-nodes tx {:ref-id "n"})))
            "- Expected 0"))

(defn test-graph
  [conn]
  (println "Nodes before creation:"
           (count (client/with-read-only-conn conn tx
                    (client/find-nodes tx {:ref-id "n"})))
            "- Expected 0")
  (println "Relationships before creation:"
           (count (client/with-read-only-conn conn tx
                    (client/find-rels tx {:ref-id "r"})))
            "- Expected 0")
  (let [node-1 (client/create-node! conn {:ref-id "n1"
                                          :labels [:person]
                                          :props {:first-name "Neo" :last-name "Anderson"}})
        node-2 (client/create-node! conn {:ref-id "n2"
                                          :labels [:person]
                                          :props {:first-name "Agent" :last-name "Smith"}})]
    (client/with-write-conn conn tx
      (client/create-graph! tx
                            (let [node-3 {:ref-id "n3" :labels [:machine] :props {:name "The Matrix"}}
                                  node-4 {:ref-id "n4" :labels [:person] :props {:first-name "Morpheus"}}]
                              {:lookups [node-1 node-2]
                               :nodes [node-3 node-4]
                               :rels [{:ref-id "r1" :from node-3 :to node-1 :type :enemy}
                                      {:ref-id "r2" :from node-3 :to node-2 :type :friend}
                                      {:ref-id "r3" :from node-3 :to node-4 :type :enemy}
                                      {:ref-id "r4" :from node-1 :to node-4 :type :friend}]
                               :returns [node-1 node-2 node-3 node-4]})))
    (println "Nodes after creation:"
             (count (client/with-read-only-conn conn tx
                      (client/find-nodes tx {:ref-id "n"})))
              "- Expected 4")
    (println "Relationships after creation:"
             (count (client/with-read-only-conn conn tx
                      (client/find-rels tx {:ref-id "r"})))
              "- Expected 4")
    (client/with-read-only-conn conn tx
      (client/get-graph tx {:nodes [{:ref-id "n1" :labels [:machine]}
                                    {:ref-id "n2" :labels [:person]}
                                    {:ref-id "n3" :labels [:person]}]
                            :rels [{:ref-id "r1" :from {:ref-id "n1"} :to {:ref-id "n2"} :type :enemy}
                                   {:ref-id "r2" :from {:ref-id "n1"} :to {:ref-id "n3"} :type :friend}]
                            :returns ["n1" "n2" "n3"]
                            :unique-by "n1"}))
    (client/delete-rel! conn {:ref-id "r"})
    (client/delete-node! conn {:ref-id "n"})
    (println "Nodes after delete:"
             (count (client/with-read-only-conn conn tx
                      (client/find-nodes tx {:ref-id "n"})))
              "- Expected 0")
    (println "Relationships after delete:"
             (count (client/with-read-only-conn conn tx
                      (client/find-rels tx {:ref-id "r"})))
              "- Expected 0")))


#_(client/disconnect conn)
