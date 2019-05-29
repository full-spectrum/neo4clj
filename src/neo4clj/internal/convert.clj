(ns neo4clj.internal.convert
  (:require [clojure.string :as str]
            [com.rpl.specter :as specter :refer [MAP-VALS]]
            [neo4clj.internal.sanitize :as sanitize]
            [java-time :as t])
  (:import [org.neo4j.driver.internal InternalEntity
                                      InternalNode
                                      InternalRelationship
                                      InternalStatementResult]
           [org.neo4j.driver.v1 Record]))

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
  [^InternalEntity entity]
  (hash-map :id (.id entity)
            :props (sanitize/clj-properties
                    (specter/transform
                     [MAP-VALS]
                     property->value
                     (into {} (.asMap entity))))))

(defmulti neo4j->clj
  "Converts a Neo4J internal entity to a Clojure Hash-Map"
  class)

(defmethod neo4j->clj InternalNode
  [^InternalNode node]
  (assoc (neo4j-entity-basics->clj node)
         :labels (sanitize/clj-labels (.labels node))))

(defmethod neo4j->clj InternalRelationship
  [^InternalRelationship rel]
  (assoc (neo4j-entity-basics->clj rel)
         :type (sanitize/clj-relation-type (.type rel))
         :start-id (.startNodeId rel)
         :end-id (.endNodeId rel)))

(defmethod neo4j->clj InternalStatementResult
  [^InternalStatementResult result]
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
  "Convert a maps keys and values into its bolt equivalent"
  [m]
  (when m
    (reduce-kv (fn [m k v] (assoc m (sanitize/cypher-property-key k) (clj-value->neo4j-value v))) {} m)))

(defn clj-node->neo4j
  "Convert a Clojure Hash-Map representation to a bolt node based on the Cypher style guide"
  [node]
  (-> node
      (update :labels sanitize/cypher-labels)
      (update :props hash-map->properties)))

(defn clj-rel->neo4j
  "Convert a Clojure Hash-Map representation to a bolt relationship based on the Cypher style guide"
  [rel]
  (-> rel
      (update :type sanitize/cypher-relation-type)
      (update :props hash-map->properties)))
