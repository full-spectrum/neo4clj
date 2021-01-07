(ns neo4clj.query-builder
  (:require [clojure.string :as str]
            [neo4clj.cypher :as cypher]
            [neo4clj.sanitize :as sanitize]))

(defn create-node-query
  "Returns the bolt query to create a node based on the given node representation"
  [{:keys [ref-id] :as node} return?]
  (str "CREATE " (cypher/node node) (when return? (str " RETURN " ref-id))))

(defn lookup
  "Takes a lookup representation and generates a bolt query

  A lookup representation needs the :reference.id to be set and
  either the :id or :labels and :props keys"
  [entity-fn {:keys [ref-id] :as entity} return?]
  (let [[base-cypher where-cypher] (entity-fn entity)]
    (str "MATCH "
         base-cypher
         (when where-cypher
           (str " WHERE " where-cypher))
         (when return? (str " RETURN " ref-id)))))

(defn lookup-node
  "Takes a node lookup representation and generates a bolt query

  A node lookup representation needs the :ref-id to be set and
  at least one of the keys :id, :labels or :props"
  [node return?]
  (lookup cypher/lookup-node node return?))

(defn lookup-rel
  "Takes a relation lookup representation and generates a bolt query

  A lookup representation needs the :reference-id, :from and :to keys to be set and
  can take the optional :id, :type and :props keys"
  [rel return?]
  (lookup cypher/lookup-relationship rel return?))

(defn index-query
  "Creates a query to modify index, allowed operations are: CREATE, DROP"
  [operation label prop-keys]
  (str operation " INDEX ON :" (sanitize/cypher-label label) "("
        (str/join ", " (map sanitize/cypher-property-key prop-keys)) ")"))

(defn lookup-non-referred-node [ref-id node]
  "Creates a query to lookup a node without a ref-id and refers it as given ref-id"
  (str (lookup-node
        (assoc node :ref-id ref-id)
        false)
       " "))

(defn create-rel-query
  "Returns the bolt query to create a one directional relationship
  based on the given relationship representation"
  [{:keys [ref-id from to] :as rel} return?]
  (let [from-ref-id (or (:ref-id from) (cypher/gen-ref-id))
        to-ref-id (or (:ref-id to) (cypher/gen-ref-id))]
    (str (lookup-non-referred-node from-ref-id from)
         (lookup-non-referred-node to-ref-id to)
         "CREATE "
         (cypher/relationship (str "(" from-ref-id ")") (str "(" to-ref-id ")") rel)
         (when return? (str " RETURN " ref-id)))))

(defn node-reference
  [known-ref-ids {:keys [ref-id] :as node}]
  (if (known-ref-ids ref-id)
    [(str "(" ref-id ")") nil]
    (cypher/lookup-node node)))

(defn non-existing-rel-query
  "Returns the bolt query where part to test that the given relationship doesn't exists"
  ([{:keys [from to] :as rel} known-ref-ids]
   (str "NOT " (cypher/relationship
                (first (node-reference known-ref-ids from))
                (first (node-reference known-ref-ids to))
                rel)))
  ([rel]
   (non-existing-rel-query rel #{})))

(defn lookup-graph-query
  [rels]
  (let [grouped-relations (group-by #(not= :not-exists (:operator %)) rels)
        exists-rels (get grouped-relations true)
        not-exists-rels (get grouped-relations false)]
    (loop [remaining-rels exists-rels
           known-ref-ids #{}
           ends-with-where? false
           query nil]
      (if (empty? remaining-rels)
        (str query
             (str/join " AND "
                       (map #(non-existing-rel-query % known-ref-ids) not-exists-rels)))
        (let [{:keys [from to] :as rel} (first remaining-rels)
              [from-node-cypher from-where-cypher] (node-reference known-ref-ids from)
              [to-node-cypher to-where-cypher] (node-reference known-ref-ids to)]
          (recur (rest remaining-rels)
                 (disj (conj known-ref-ids (:ref-id from) (:ref-id to) (:ref-id rel)) nil)
                 (or from-where-cypher to-where-cypher)
                 (str (when query
                        (str query " WITH " (str/join "," known-ref-ids) " "))
                      (lookup-rel rel false))))))))

(defn get-return-ref-ids
  "Takes a list or vector of entity representatios or ref-ids and
  returns a list of ref-ids"
  [^clojure.lang.IPersistentStack returns]
  (map #(if (map? %) (:ref-id %) %) returns))

(defn- create-graph-rel-query
  "Returns a query string to be used specifically inside a create graph
  cypher query. The query represents the creation of a one directional
  relationship based on the given relationship representation.

  The relationship can only be created between already MATCHED'ed
  nodes in the graph query"
  [{:keys [ref-id from to] :as rel}]
  (str "CREATE "
       (cypher/relationship (str "(" (:ref-id from) ")") (str "(" (:ref-id to) ")") rel)))

(defn create-graph-query
  "Takes a graph representation and creates the nodes and relationship
  defined and returns any aliases specified in the representation"
  [{:keys [lookups nodes rels returns]}]
  (str/join " " (concat
                 (map #(lookup-node % false) lookups)
                 (map #(create-node-query % false) nodes)
                 (map create-graph-rel-query rels)
                 (when-not (empty? returns)
                   (vector (str "RETURN " (str/join "," (get-return-ref-ids returns))))))))

(defn get-graph-query
  "Takes a graph representation and fetches the nodes and relationship defined
  and returns any aliases specified in the representation"
  [{:keys [rels returns]}]
  (str (lookup-graph-query rels)
       (when-not (empty? returns)
         (str " RETURN " (str/join "," (get-return-ref-ids returns))))))

(defn modify-labels-query
  "Takes a operation and a neo4j node representation, along with a collection
  of labels and either sets or removes them

  Allowed operations are: SET, REMOVE"
  [operation {:keys [ref-id] :or {ref-id "n"} :as node} labels]
  (str (lookup-node (assoc node :ref-id ref-id) false) " "
       operation " " ref-id (cypher/labels labels)))

(defn modify-properties-query
  "Takes a neo4j entity representation, along with a properties map

  Allowed operations are: =, +="
  [operation {:keys [ref-id] :or {ref-id "e"} :as entity} props]
  (str (lookup-node (assoc entity :ref-id ref-id) false) " SET "
       ref-id " " operation (cypher/properties props)))

(defn delete-node
  "Takes a neo4j node representation and deletes it"
  [{:keys [ref-id] :or {ref-id "n"} :as node}]
  (str (lookup-node (assoc node :ref-id ref-id) false) " DELETE " ref-id))

(defn delete-rel
  "Takes a neo4j relationship representation and deletes it"
  [{:keys [ref-id] :or {ref-id "r"} :as rel}]
  (str (lookup-rel (assoc rel :ref-id ref-id) false) " DELETE " ref-id))
