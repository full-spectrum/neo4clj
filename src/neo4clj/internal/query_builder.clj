(ns neo4clj.internal.query-builder
  (:require [clojure.string :as str]
            [neo4clj.internal.convert :as convert]
            [neo4clj.internal.sanitize :as sanitize]))

(defn generate-ref-id
  "Generate a unique id that can be used to reference an Neo4j entity
  until they are assigned one by the database, which happens when
  they are stored."
  []
  (str (gensym)))

(defn properties-query
  "Convert a map into its bolt query equivalent"
  [m]
  (str "{" (str/join ", " (map (fn [[k v]] (str k ": " v)) m)) "}"))

(defn create-node-query
  "Returns the bolt query to create a node based on the given node representation"
  [node return?]
  (let [{:keys [ref-id labels props]} (convert/clj-node->neo4j node)]
    (str "CREATE (" ref-id
         (when (not-empty labels)
           (str ":" (str/join ":" labels)))
         " "
         (properties-query props) ")"
         (when return? (str " RETURN " ref-id)))))

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

(defn lookup-query
  "Takes a lookup representation and generates a bolt query

  A lookup representation needs the :reference.id to be set and
  either the :id or :labels and :properties keys"
  [{:keys [id ref-id labels props]} return?]
  (str "MATCH (" ref-id
       (if id
         (str ") WHERE ID(" ref-id ") = " id)
         (str ":" (str/join ":" (sanitize/cypher-labels labels))
              (if (map? props)
                (str " " (properties-query (convert/hash-map->properties props)) ")")
                (str ") WHERE " (where-query ref-id props)))))
       (when return? (str " RETURN " ref-id))))

(defn index-query
  "Creates a query to modify index, allowed operations are: CREATE, DROP"
  [operation label prop-key]
  (str operation " INDEX ON " (sanitize/cypher-label label) "("
        (sanitize/cypher-property-key prop-key) ")"))

(defn create-relationship-query
  "Returns the bolt query to create a one directional relationship
  based on the given relationship representation"
  [rel return?]
  (let [{:keys [ref-id from to type props]} (convert/clj-rel->neo4j rel)
        from-ref-id (or (:ref-id from) (generate-ref-id))
        to-ref-id (or (:ref-id to) (generate-ref-id))]
    (str (when-not (:ref-id from)
           (str (lookup-query
                 (assoc from :ref-id from-ref-id)
                 false)
                " "))
         (when-not (:ref-id to)
           (str (lookup-query
                 (assoc to :ref-id to-ref-id)
                 false)
                " "))
         "CREATE (" from-ref-id ")-[" ref-id ":" type " "
         (properties-query props) "]->(" to-ref-id ")"
         (when return? (str " RETURN " ref-id)))))

(defn create-graph-query
  "Takes a graph representation and creates the nodes and relationship defined
  and returns any aliases specified in the representation"
  [{:keys [lookups nodes relationships return-aliases]}]
  (str/join " " (concat
                 (map #(lookup-query % false) lookups)
                 (map #(create-node-query % false) nodes)
                 (map #(create-relationship-query % false) relationships)
                 (when-not (empty? return-aliases)
                   (vector (str "RETURN " (str/join "," return-aliases)))))))

(defn modify-labels-query
  "Takes a operation and a neo4j object representation, along with a collection
  of labels and either sets or removes them

  Allowed operations are: SET, REMOVE"
  [operation {:keys [ref-id] :as entity} labels]
  (str (lookup-query entity false) " " operation " "
       ref-id ":" (str/join ":" (sanitize/cypher-labels labels))))

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
