(ns neo4clj.convert
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [neo4clj.sanitize :as sanitize]
            [java-time :as t])
  (:import [org.neo4j.driver Record
                             Result
                             Values]
           [org.neo4j.driver.types Entity
                                   Node
                                   Relationship]
           [org.neo4j.driver.internal.value MapValue]))

(defn neo4j-entity-basics->clj
  "Convert a given Neo4J internal object into a hash-map with the basic entity informations"
  [^Entity entity]
  (hash-map :id (.id entity)
            :props (sanitize/clj-properties
                    (into {} (.asMap entity)))))

(defmulti neo4j->clj
  "Converts a Neo4J internal entity to a Clojure Hash-Map"
  class)

(defmethod neo4j->clj nil
  [entity]
  nil)

(defmethod neo4j->clj java.util.List
  [entity]
  (into [] entity))

(defmethod neo4j->clj java.util.Map
  [entity]
  (into {} entity))

(defmethod neo4j->clj Node
  [^Node node]
  (assoc (neo4j-entity-basics->clj node)
         :labels (sanitize/clj-labels (.labels node))))

(defmethod neo4j->clj Relationship
  [^Relationship rel]
  (assoc (neo4j-entity-basics->clj rel)
         :type (sanitize/clj-relation-type (.type rel))
         :start-id (.startNodeId rel)
         :end-id (.endNodeId rel)))

(defmethod neo4j->clj Result
  [^Result result]
  (->> (iterator-seq result)
       (map (fn [^Record r] (.asMap r)))
       (map #(reduce (fn [m [k v]]
                       (assoc m k (let [converted-v (neo4j->clj v)]
                                    (if (map? converted-v)
                                      (assoc converted-v :ref-id k)
                                      converted-v)))) {} %))
       (walk/prewalk neo4j->clj)))

(defmethod neo4j->clj :default
  [entity]
  entity)

(defn clj-value->neo4j-value
  "Convert a given clojure primitive into its bolt query equivalent
  If given a vector or list, all elements within needs to be of the same type.
  This is a limitation in Neo4j not Neo4clj"
  [value]
  (cond
    (string? value) (str "'" value "'")
    (number? value) value
    (nil? value) "NULL"
    (boolean? value) (str/upper-case value)
    (keyword? value) (str "'" (name value) "'")
    (or (set? value) (instance? clojure.lang.IPersistentStack value)) (str "[" (str/join ", " (map clj-value->neo4j-value value)) "]")
    (instance? java.time.Instant value) (str "datetime(\"" (t/format :iso-instant value) "\")")
    :else value))

(defn hash-map->properties
  "Convert map keys and values into its bolt equivalent"
  [^clojure.lang.IPersistentMap m]
  (when m
    (reduce-kv (fn [m k v] (assoc m (sanitize/cypher-property-key k) (clj-value->neo4j-value v))) {} m)))

(defn clj-relationship->neo4j
  "Convert a Clojure Hash-Map representation to a bolt relationship based on the Cypher style guide"
  [^clojure.lang.IPersistentMap rel]
  (-> rel
      (update :type sanitize/cypher-relation-type)
      (update :props hash-map->properties)))

(defn clj-parameter->neo4j
  "Convert a given clojure primitive into its bolt parameter equivalent
  If given a vector or list, all elements within needs to be of the same type.
  This is a limitation in Neo4j not Neo4clj"
  [value]
  (cond
    (keyword? value) (name value)
    (instance? java.time.Instant value) (java-time/zoned-date-time value "UTC")
    :else value))

(defn clj-parameters->neo4j
  "Convert a Clojure parameter map to a Neo4j parameter array"
  ^MapValue [^clojure.lang.IPersistentMap params]
  (->> params
       (walk/postwalk clj-parameter->neo4j)
       (mapcat identity)
       (into-array Object)
       Values/parameters))
