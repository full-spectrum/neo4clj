(ns neo4clj.java-interop
  (:require [neo4clj.convert :as convert])
  (:import [org.neo4j.driver.internal.logging ConsoleLogging]
           [org.neo4j.driver.v1 AuthTokens Config Config$EncryptionLevel Driver
                                GraphDatabase StatementRunner]
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
  ^Config [^clojure.lang.IPersistentMap opts]
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

(defn execute
  "Runs the given query with optional parameters on the given Neo4J session/transaction"
  ([^StatementRunner runner ^String query]
   (execute runner query {}))
  ([^StatementRunner runner ^String query ^Map params]
   (convert/neo4j->clj (.run runner query (convert/clj-parameters->neo4j params)))))
