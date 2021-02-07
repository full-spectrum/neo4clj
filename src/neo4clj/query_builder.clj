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

  A lookup representation needs the :ref-id to be set and
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

  A lookup representation needs the :ref-id, :from and :to keys to be set and
  can take the optional :id, :type and :props keys"
  [rel return?]
  (lookup cypher/lookup-relationship rel return?))

(defn index-query
  "Creates a query to modify index, allowed operations are: CREATE, DROP"
  [operation label prop-keys]
  (str operation " INDEX ON :" (sanitize/cypher-label label) "("
       (str/join ", " (map sanitize/cypher-property-key prop-keys)) ")"))

(defn- lookup-non-referred-node [ref-id node]
  "Creates a query to lookup a node and refers it as given ref-id"
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

(defn- node-ref-id
  "Takes a map representation of a Node and returns the key :ref-id or
  a String and returns the string"
  [{:keys [ref-id] :as node}]
  (or ref-id node))

(defn- get-return-ref-ids
  "Takes a list or vector of entity representatios or ref-ids and
  returns a list of ref-ids"
  [^clojure.lang.IPersistentStack returns]
  (map node-ref-id returns))

(defn- node-reference
  "Takes a set of already matched, a list of node entries to lookup in
  and the node to check. The function returns the node reference query
  and optional where parts.

  If the given node is already matched, the reference is
  returned. Otherwise the node query i generated through the list of
  node entries or directly on the given node if not in the list.

  The optional flag as-lookup? dictates wheter the query should be a
  lookup or a raw node reference.

  The optional flag unknown-only? returns nil for already known
  ref-ids"
  ([known-ref-ids node-entries node]
   (node-reference known-ref-ids node-entries node true false))
  ([known-ref-ids node-entries node as-lookup?]
   (node-reference known-ref-ids node-entries node as-lookup? false))
  ([known-ref-ids node-entries node as-lookup? unknown-only?]
   (let [ref-id (node-ref-id node)]
     (if-let [known-ref? (known-ref-ids ref-id)]
       (when (not unknown-only?)
         [(str "(" ref-id ")") nil])
       ((if as-lookup? cypher/lookup-node #(vector (cypher/node %) nil)) (or (node-entries ref-id) node))))))

(defn- generate-relation-queries
  "Takes a set of known reference id's, a map of node entries to
  lookup and a list of relationships to create queries for.

  If a nodes reference id is already known, the node is reference by
  it, else a lookup query is inserted instead."
  [known-ref-ids node-entries rels as-lookup?]
  (loop [remaining-rels rels
         known-refs known-ref-ids
         rel-queries []
         where-parts []]
    (if (empty? remaining-rels)
      [rel-queries where-parts known-refs]
      (let [{:keys [from to] :as rel} (first remaining-rels)
            [from-query from-where] (node-reference known-refs node-entries from as-lookup?)
            [to-query to-where] (node-reference known-refs node-entries to as-lookup?)]
        (recur (rest remaining-rels)
               (conj known-refs (node-ref-id from) (node-ref-id to))
               (conj rel-queries (cypher/relationship from-query to-query rel))
               (concat where-parts (remove nil? [from-where to-where])))))))

(defn create-graph-query
  "Takes a graph representation and creates the nodes and relationship
  defined and returns any aliases specified in the representation"
  [{:keys [lookups nodes rels returns]}]
  (str/join " " (concat
                 (map #(lookup-node % false) lookups)
                 [(str "CREATE " (str/join " CREATE " (first (generate-relation-queries
                                                              (set (map :ref-id lookups))
                                                              (reduce #(assoc %1 (:ref-id %2) %2) {} nodes)
                                                              rels
                                                              false))))]
                 (when-not (empty? returns)
                   (vector (str "RETURN " (str/join ", " (get-return-ref-ids returns))))))))

(defn- lookup-unmatched-nodes
  "Takes a list of node entries, known reference ids and a collection of relations.
  Returns a collection of node queries and where quiries for the missing node reference ids."
  [node-entries known-ref-ids rels]
  (loop [unmatched-nodes []
         remaining-rels rels
         known-refs known-ref-ids]
    (if (empty? remaining-rels)
      (vector (remove nil? (map first unmatched-nodes))
              (remove nil? (map second unmatched-nodes)))
      (let [{:keys [from to]} (first rels)]
        (recur (conj unmatched-nodes
                     (node-reference known-refs node-entries from true true)
                     (node-reference known-refs node-entries to true true))
               (rest remaining-rels)
               (conj known-refs (node-ref-id from) (node-ref-id to)))))))

(defn- lookup-non-existing-graph-rel
  "Takes a relation lookup representation and generates a bolt query with a
  where part to test that the given relationship doesn't exists"
  ([{:keys [from to] :as rel}]
   (str "NOT " (cypher/relationship
                (str "(" (or (:ref-id from) from) ")")
                (str "(" (or (:ref-id to) to) ")")
                (dissoc rel :ref-id)))))

(defn- lookup-graph-single-matches-and-wheres
  "Takes a list of node entries, known reference ids, relations to
  check for non-existence and a collection of where queries.
  Returns a query with missing node matches, non existent relation
  wheres and other wheres given."
  [node-entries known-ref-ids not-exists-rels where-parts]
  (let [[unmatched-node-lookups unmatched-node-wheres] (lookup-unmatched-nodes node-entries known-ref-ids not-exists-rels)]
    (str (when-not (empty? unmatched-node-lookups)
           (str " MATCH " (str/join " MATCH " unmatched-node-lookups)))
         " WHERE "
         (str/join " AND " (concat where-parts
                                   unmatched-node-wheres
                                   (map lookup-non-existing-graph-rel not-exists-rels))))))

(defn lookup-graph-query
  "Takes a list of node-entries and relations. Returns a query string
  to lookup the nodes represented by the given relationships."
  [node-entries rels]
  (let [grouped-relations (group-by #(false? (:exists %)) rels)
        exists-rels (get grouped-relations false)
        not-exists-rels (get grouped-relations true)
        [rel-queries where-parts known-ref-ids] (generate-relation-queries #{} node-entries exists-rels true)]
    (str/trim (str (when-not (empty? rel-queries)
                     (str "MATCH " (str/join " MATCH " rel-queries)))
                   (when (or (not-empty not-exists-rels) (not-empty where-parts))
                     (lookup-graph-single-matches-and-wheres node-entries known-ref-ids not-exists-rels where-parts))))))

(defn get-graph-query
  "Takes a graph representation and fetches the nodes and relationship defined
  and returns any aliases specified in the representation"
  [{:keys [nodes rels returns]}]
  (let [node-entries (reduce #(assoc %1 (:ref-id %2) %2) {} nodes)]
    (str (lookup-graph-query node-entries rels)
         (when-not (empty? returns)
           (str " RETURN " (str/join ", " (get-return-ref-ids returns)))))))

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
