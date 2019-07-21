(ns neo4clj.convert
  (:require [clojure.string :as str]
            [com.rpl.specter :as specter :refer [MAP-VALS]]
            [neo4clj.sanitize :as sanitize]
            [java-time :as t])
  (:import [org.neo4j.driver.v1 Values]
           [org.neo4j.driver.v1.types Entity
                                      Node
                                      Relationship]
           [org.neo4j.driver.v1 Record
                                StatementResult]))

;; Pattern used to recognize date-time values from Neo4J
(def date-time-pattern #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}")

(defn property->value
  "Convert a given bolt property into its clojure equivalent"
  [prop]
  (if (and (string? prop) (re-find date-time-pattern prop))
    (t/instant prop)
    prop))

(defn neo4j-entity-basics->clj
  "Convert a given Neo4J internal object into a hash-map with the basic entity informations"
  [^Entity entity]
  (hash-map :id (.id entity)
            :props (sanitize/clj-properties
                    (specter/transform
                     [MAP-VALS]
                     property->value
                     (into {} (.asMap entity))))))

(defmulti neo4j->clj
  "Converts a Neo4J internal entity to a Clojure Hash-Map"
  class)

(defmethod neo4j->clj nil
  [entity]
  nil)

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

(defmethod neo4j->clj StatementResult
  [^StatementResult result]
  (->> (iterator-seq result)
       (map (fn [^Record r] (.asMap r)))
       (map #(reduce (fn [m [k v]] (assoc m k (neo4j->clj v))) {} %))))

(defn clj-value->neo4j-value
  "Convert a given clojure primitive into its bolt query equivalent"
  [value]
  (cond
    (string? value) (str "'" value "'")
    (number? value) value
    (nil? value) "NULL"
    (boolean? value) (str/upper-case value)
    (keyword? value) (str "'" (name value) "'")
    (instance? java.time.Instant value) (str "'" (t/format :iso-instant value) "'")
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

(defn clj-parameters->neo4j
  "Convert a Clojure parameter map to a Neo4j parameter array"
  [^clojure.lang.IPersistentMap params]
  (->> params
       clojure.walk/stringify-keys
       (mapcat identity)
       (into-array Object)
       Values/parameters))
