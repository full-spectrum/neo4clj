(ns full-spectrum.neo4clj.internal.neo4j
  (:require [clojure.string :as str]
            [com.rpl.specter :as specter :refer [MAP-VALS]]
            [full-spectrum.neo4clj.internal.sanitize :as sanitize]
            [java-time :as t])
  (:import [org.neo4j.driver.internal InternalEntity InternalNode InternalRelationship
                                      InternalStatementResult]
           [org.neo4j.driver.internal.logging ConsoleLogging]
           [org.neo4j.driver.v1 AuthTokens Config Config$EncryptionLevel Driver
                                GraphDatabase Record Session StatementRunner
                                Transaction]
           [java.util Map]
           [java.util.logging Level]))

(defn build-config
  "Generates a new Neo4J Config object based on the given options

  Supports the current options:
  :logging :level [:all :error :warning :info :off] - defaults to :warning
  :encryption [:required :off] - defaults to :required"
  ^Config [opts]
  (let [logging-level (ConsoleLogging. (condp = (get-in [:logging :level] opts)
                                         :all     Level/ALL
                                         :error   Level/SEVERE
                                         :warning Level/WARNING
                                         :info    Level/INFO
                                         :off    Level/OFF
                                         Level/WARNING))
        encryption-level (if (= (:encryption opts) :off)
                           (. Config$EncryptionLevel NONE)
                           (. Config$EncryptionLevel REQUIRED))]
    (.. (Config/build)
        (withLogging logging-level)
        (withEncryptionLevel encryption-level)
        toConfig)))

(defn connect
  "Create a new Neo4J connection to the given url, with optional credentials and configuration"
  (^Driver [^String url]
   (GraphDatabase/driver url))
  (^Driver [^String url ^Config cfg]
   (GraphDatabase/driver url))
  (^Driver [^String url ^String usr ^String pwd]
   (GraphDatabase/driver url (AuthTokens/basic usr pwd)))
  (^Driver [^String url ^String usr ^String pwd ^Config cfg]
   (GraphDatabase/driver url (AuthTokens/basic usr pwd) cfg)))

(defn disconnect
  "Close the given Neo4J connection"
  [^Driver driver]
  (.close driver))

(defn create-session
  "Create a new session on the given Neo4J connection"
  ^Session [^Driver driver]
  (.session driver))

;; Pattern used to recognize date-time values from Neo4J
(def date-time-pattern #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}")

(defn- property->value
  "Convert a given bolt property into its clojure equivalent"
  [prop]
  (if (re-find date-time-pattern prop)
    (t/instant prop)
    prop))

(defn- neo4j-entity-basics->clj
  "Convert a given Neo4J internal object into a hash-map with the basic entity informations"
  [^InternalEntity entity]
  (hash-map :id (.id entity)
            :labels (sanitize/clj-labels (.labels entity))
            :props (sanitize/clj-properties
                    (specter/transform
                     [MAP-VALS]
                     property->value
                     (into {} (.asMap entity))))))

(defmulti neo4j->clj
  "Converts a Neo4J internal entity to a Clojure Hash-Map"
  (fn [entity] (class entity)))

(defmethod neo4j->clj InternalNode
  [^InternalNode node]
  (neo4j-entity-basics->clj node))

(defmethod neo4j->clj InternalRelationship
  [^InternalRelationship rel]
  (assoc (neo4j-entity-basics->clj rel)
         :start-id (.startNodeId rel)
         :end-id (.endNodeId rel)))

(defmethod neo4j->clj InternalStatementResult
  [^InternalStatementResult result]
  (->> (iterator-seq result)
       (map (fn [^Record r] (.asMap r)))
       (map #(reduce-kv (fn [m k v] (assoc m k (neo4j->clj v))) {} %))))

#_(map #(reduce (fn [m [k v]] (assoc m k (neo4j->clj v))) {} %)
       (map (fn [^Record r] (.asMap r)) (iterator-seq result)))

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
  (reduce-kv (fn [m k v] (assoc m (sanitize/cypher-property-key k) (clj-value->neo4j-value v))) {} m))

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

(defn begin-transaction
  "Start a new transaction on the given Neo4J session"
  ^Transaction [^Session session]
  (.beginTransaction session))

(defn commit-transaction
  "Commits the given transaction"
  [^Transaction trans]
  (.success trans))

(defn rollback-transaction
  "Rolls the given transaction back"
  [^Transaction trans]
  (.failure trans))

(defn- make-success-transaction [tx]
  (proxy [org.neo4j.driver.v1.Transaction] []
    (run
      ([q] (.run tx q))
      ([q p] (.run tx q p)))
    (success [] (.success tx))
    (failure [] (.failure tx))

    ;; We only want to auto-success to ensure persistence
    (close []
      (.success tx)
      (.close tx))))

(defn execute
  "Runs the given query with optional parameters on the given Neo4J session/transaction"
  ([^StatementRunner runner ^String query]
   (execute runner query {}))
  ([^StatementRunner runner ^String query ^Map params]
   (neo4j->clj (.run runner query params))))

