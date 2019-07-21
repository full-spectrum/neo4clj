(ns neo4clj.query-builder
  (:require [clojure.string :as str]
            [neo4clj.cypher :as cypher]
            [neo4clj.sanitize :as sanitize]))

(defn create-node-query
  "Returns the bolt query to create a node based on the given node representation"
  [{:keys [ref-id] :as node} return?]
  (str "CREATE " (cypher/node node) (when return? (str " RETURN " ref-id))))

(defn lookup-query
  "Takes a lookup representation and generates a bolt query

  A lookup representation needs the :reference.id to be set and
  either the :id or :labels and :properties keys"
  [{:keys [ref-id] :as lookup} return?]
  (let [cypher-lookup (cypher/lookup lookup)]
    (str "MATCH "
         (str (first cypher-lookup)
              (when (second cypher-lookup)
                (str " WHERE " (second cypher-lookup))))
         (when return? (str " RETURN " ref-id)))))

(defn index-query
  "Creates a query to modify index, allowed operations are: CREATE, DROP"
  [operation label prop-keys]
  (str operation " INDEX ON :" (sanitize/cypher-label label) "("
        (str/join ", " (map sanitize/cypher-property-key prop-keys)) ")"))

(defn lookup-non-referred-node [ref-id node]
  "Creates a query to lookup a node without a ref-id and refers it as given ref-id"
  (when-not (:ref-id node)
    (str (lookup-query
          (assoc node :ref-id ref-id)
          false)
         " ")))

(defn create-relationship-query
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

(defn create-graph-query
  "Takes a graph representation and creates the nodes and relationship defined
  and returns any aliases specified in the representation"
  [{:keys [lookups nodes relationships returns]}]
  (str/join " " (concat
                 (map #(lookup-query % false) lookups)
                 (map #(create-node-query % false) nodes)
                 (map #(create-relationship-query % false) relationships)
                 (when-not (empty? returns)
                   (vector (str "RETURN " (str/join "," returns)))))))

(defn node-reference
  [nodes {:keys [ref-id] :as node}]
  (let [found-node (first (filter #(= ref-id (:ref-id %)) nodes))]
    (vector
     found-node
     (if found-node
       (cypher/lookup found-node)
       [(str "(" ref-id ")")]))))

(defn lookup-graph-query
  [relationships nodes]
  (loop [rels relationships
         nodes (set nodes)
         withs #{}
         query nil]
    (if (empty? rels)
      query
      (let [{:keys [from to] :as rel} (first rels)
            [from-node [from-node-cypher from-where-cypher]] (node-reference nodes from)
            [to-node [to-node-cypher to-where-cypher]] (node-reference nodes to)]
        (recur (rest rels)
               (remove #{from-node to-node} nodes)
               (conj withs (:ref-id from) (:ref-id to))
               (str (when query
                      (str query " WITH " (clojure.string/join "," withs) " "))
                    (str "MATCH "
                         (cypher/relationship from-node-cypher to-node-cypher rel)
                         (when (or from-where-cypher to-where-cypher)
                           (str " WHERE " (clojure.string/join " AND " (remove nil? [from-where-cypher to-where-cypher])))))))))))

(defn get-graph-query
  "Takes a graph representation and fetches the nodes and relationship defined
  and returns any aliases specified in the representation"
  [{:keys [nodes relationships returns]}]
  (str (lookup-graph-query relationships nodes)
       (when-not (empty? returns)
         (str " RETURN " (str/join "," returns)))))

(defn modify-labels-query
  "Takes a operation and a neo4j node representation, along with a collection
  of labels and either sets or removes them

  Allowed operations are: SET, REMOVE"
  [operation {:keys [ref-id] :or {ref-id "n"} :as node} labels]
  (str (lookup-query (assoc node :ref-id ref-id) false) " "
       operation " " ref-id (cypher/labels labels)))

(defn modify-properties-query
  "Takes a neo4j entity representation, along with a properties map

  Allowed operations are: =, +="
  [operation {:keys [ref-id] :or {ref-id "e"} :as entity} props]
  (str (lookup-query (assoc entity :ref-id ref-id) false) " SET "
       ref-id " " operation (cypher/properties props)))

(defn delete-query
  "Takes a neo4j entity representation and deletes it"
  [{:keys [ref-id] :or {ref-id "e"} :as entity}]
  (str (lookup-query (assoc entity :ref-id ref-id) false) " DELETE " ref-id))
