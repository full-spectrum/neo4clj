(ns full-spectrum.neo4clj.internal.neo4j
  (:require [full-spectrum.neo4clj.internal.convert :as convert])
  (:import [org.neo4j.driver.internal.logging ConsoleLogging]
           [org.neo4j.driver.v1 AuthTokens Config Config$EncryptionLevel Driver
                                GraphDatabase Session StatementRunner Transaction]
           [java.util Map]
           [java.util.logging Level]))

(def log-level-mapping
  "Convenience for allowing to use Clojure keywords to describe Neo4J log level."
  {:all   Level/ALL
   :error Level/SEVERE
   :warn  Level/WARNING
   :info  Level/INFO
   :off   Level/OFF})

(defn build-config
  "Generates a new Neo4J Config object based on the given options

  Supports the current options:
  :log :level [:all :error :warn :info :off] - defaults to :warn
  :encryption [:required :none] - defaults to :required"
  ^Config [opts]
  (let [log-level (get-in opts [:log :level] :warn)
        encryption-level (if (= (:encryption opts) :none)
                           Config$EncryptionLevel/NONE
                           Config$EncryptionLevel/REQUIRED)]
    (.. (Config/build)
        (withLogging (ConsoleLogging. (get log-level-mapping log-level)))
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
   (convert/neo4j->clj (.run runner query params))))
