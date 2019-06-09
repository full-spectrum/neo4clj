(ns neo4clj.cypher
  (:require [clojure.string :as str]
            [neo4clj.convert :as convert]
            [neo4clj.sanitize :as sanitize]))

(defn gen-ref-id
  "Generate a unique id that can be used to reference an Neo4j entity
  until they are assigned one by the database, which happens when
  they are stored."
  []
  (str (gensym)))

(defmulti where
  "Returns the bolt query where representation based on the given criterias"
  (fn [ref-id criterias] (class criterias)))

(defmethod where clojure.lang.APersistentMap
  [ref-id criterias]
  (->>
   (convert/hash-map->properties criterias)
   (map (fn [[k v]] (str ref-id "." k " = " v)))
   (str/join " AND ")))

(defmethod where clojure.lang.APersistentSet
  [ref-id criterias]
  (str/join " OR " (map (partial where ref-id) criterias)))

(defmethod where clojure.lang.IPersistentCollection
  [ref-id criterias]
  (where ref-id (set criterias)))

(defmethod where nil
  [ref-id criterias]
  "")

(defn properties
  "Convert a map into its bolt query equivalent"
  [props]
  (let [converted-props (convert/hash-map->properties props)]
    (when converted-props
      (str " {" (str/join ", " (map (fn [[k v]] (str k ": " v)) converted-props)) "}"))))

(defn labels
  "Takes a collection of labels (keywords) and returns a Cypher string
  representing the labels. Order is reversed but doesn't matter."
  [labels]
  (->>
   labels
   (reduce #(conj %1 (sanitize/cypher-label %2) ":") '())
   str/join))

(defn node
  "Takes a node representation and returns its cypher equivalent."
  [{:keys [id ref-id props] :as node}]
  (str "(" ref-id (labels (:labels node))
       (when props (properties props))
       ")"))

(defn relationship
  "Takes a relationship representation and returns its cypher equivalent"
  [from to {:keys [ref-id type props]}]
  (str "(" from ")-[" ref-id
       (when type (str ":" (sanitize/cypher-relation-type type)))
       (when props (properties props))
       "]->(" to ")"))

(defn lookup
  "Takes a lookup representation and returns its cypher equvalent

  The return value is an vector with the first part being the actual entity and
  the second is the where clause for the lookup."
  [{:keys [id ref-id props] :as lookup}]
  (if id
    [(str "(" ref-id ")")
     (str "ID(" ref-id ") = " id)]
    [(str "(" ref-id (labels (:labels lookup))
          (when (map? props) (properties props))
          ")")
     (when (and props (not (map? props))) (where ref-id props))]))
