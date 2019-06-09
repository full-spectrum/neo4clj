(ns neo4clj.query-builder
  (:require [clojure.string :as str]
            [neo4clj.convert :as convert]
            [neo4clj.cypher :as cypher]
            [neo4clj.sanitize :as sanitize]))

(defn properties-query
  "Convert a map into its bolt query equivalent"
  [m]
  (when (map? m)
    (str "{" (str/join ", " (map (fn [[k v]] (str k ": " v)) m)) "}")))

(defmulti where-query
  "Returns the bolt query where representation based on the given criterias"
  (fn [ref-id criterias] (class criterias)))

(defmethod where-query clojure.lang.APersistentMap
  [ref-id criterias]
  (->>
   (convert/hash-map->properties criterias)
   (map (fn [[k v]] (str ref-id "." k " = " v)))
   (str/join " AND ")))

(defmethod where-query clojure.lang.APersistentSet
  [ref-id criterias]
  (str/join " OR " (map (partial where-query ref-id) criterias)))

(defmethod where-query clojure.lang.IPersistentCollection
  [ref-id criterias]
  (where-query ref-id (set criterias)))

(defmethod where-query nil
  [ref-id criterias]
  "")

(defn node-representation
  "Takes a node representation and returns its cypher equivalent

  The return value is an vector with the first part being the actual node
  and the second the where clause for the node lookup"
  [{:keys [id ref-id labels props]}]
  (if id
    [(str "(" ref-id ")")
     (str "ID(" ref-id ") = " id)]
    [(str "(" ref-id (cypher/labels labels)
          (when (map? props)
            (str " " (properties-query (convert/hash-map->properties props))))
          ")")
     (when (and props (not (map? props))) (where-query ref-id props))]))

(defn create-node-query
  "Returns the bolt query to create a node based on the given node representation"
  [{:keys [ref-id] :as node} return?]
  (str "CREATE "
       (first (node-representation node))
       (when return? (str " RETURN " ref-id))))

(defn lookup-query
  "Takes a lookup representation and generates a bolt query

  A lookup representation needs the :reference.id to be set and
  either the :id or :labels and :properties keys"
  [{:keys [ref-id] :as node} return?]
  (let [cypher-node (node-representation node)]
    (str "MATCH "
         (str (first cypher-node)
              (when (second cypher-node)
                (str " WHERE " (second cypher-node))))
         (when return? (str " RETURN " ref-id)))))

(defn index-query
  "Creates a query to modify index, allowed operations are: CREATE, DROP"
  [operation label prop-key]
  (str operation " INDEX ON " (sanitize/cypher-label label) "("
        (sanitize/cypher-property-key prop-key) ")"))

(defn lookup-non-referred-node [ref-id node]
  "Creates a query to lookup a node without a ref-id and refers it as given ref-id"
  (when-not (:ref-id node)
    (str (lookup-query
          (assoc node :ref-id ref-id)
          false)
         " ")))

(defn relationship-representation
  "Takes a relationship representation and returns its cypher equivalent"
  [from-cypher to-cypher {:keys [ref-id type props]}]
  (str from-cypher "-[" ref-id
       (when type (str ":" (sanitize/cypher-relation-type type)))
       (when (map? props) (str " " (properties-query (convert/hash-map->properties props))))
       "]->" to-cypher))

(defn create-relationship-query
  "Returns the bolt query to create a one directional relationship
  based on the given relationship representation"
  [{:keys [ref-id from to] :as rel} return?]
  (let [from-ref-id (or (:ref-id from) (cypher/gen-ref-id))
        to-ref-id (or (:ref-id to) (cypher/gen-ref-id))]
    (str (lookup-non-referred-node from-ref-id from)
         (lookup-non-referred-node to-ref-id to)
         "CREATE "
         (relationship-representation (str "(" from-ref-id ")")
                                      (str "(" to-ref-id ")")
                                      rel)
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


(defn modify-labels-query
  "Takes a operation and a neo4j object representation, along with a collection
  of labels and either sets or removes them

  Allowed operations are: SET, REMOVE"
  [operation {:keys [ref-id] :as entity} labels]
  (str (lookup-query entity false) " " operation " " ref-id (cypher/labels labels)))

(defn modify-properties-query
  "Takes a neo4j entity representation, along with a properties map

  Allowed operations are: =, +="
  [operation {:keys [ref-id] :as neo4j-entity} props]
  (str (lookup-query neo4j-entity false) " SET "
       ref-id " " operation " " (properties-query (convert/hash-map->properties props))))

(defn delete-query
  "Takes a neo4j entity representation and deletes it"
  [{:keys [ref-id] :as neo4j-entity}]
  (str (lookup-query neo4j-entity false) " DELETE " ref-id))
