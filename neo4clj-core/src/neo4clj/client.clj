(ns neo4clj.client
  (:require [clojure.string :as str]
            [neo4clj.cypher :as cypher]
            [neo4clj.java-interop :as java-interop]
            [neo4clj.query-builder :as builder])
  (:import  [org.neo4j.driver Driver Session QueryRunner SessionConfig Transaction TransactionWork]))

(defprotocol Closeable
  (close [this]))

(defrecord IConnection [^Driver driver ^clojure.lang.IPersistentMap opts]
  Closeable
  (close [this] (.close ^Driver (:driver this))))

(defrecord ISession [^Session session ^clojure.lang.IPersistentMap opts]
  Closeable
  (close [this] (.close ^Session (:session this))))

(defrecord ITransaction [^Transaction transaction ^clojure.lang.IPersistentMap opts]
  Closeable
  (close [this] (.close ^Transaction (:transaction this))))

(defn connect
  "Connect through bolt to the given neo4j server

  Supports the current options:
  :log :level [:all :error :warn :info :off] - defaults to :warn
  :encryption [:required :none] - defaults to :required
  :database - defaults to nil
  :enforce-utc [true false] - default to false"
  (^IConnection [^String url]
   (connect  url {}))
  (^IConnection [^String url ^clojure.lang.IPersistentMap opts]
   (IConnection. (java-interop/connect url (java-interop/build-config opts)) opts))
  (^IConnection [^String url ^String usr ^String pwd]
   (connect url usr pwd {}))
  (^IConnection [^String url ^String usr ^String pwd ^clojure.lang.IPersistentMap opts]
   (IConnection. (java-interop/connect url usr pwd (java-interop/build-config opts)) opts)))

(defn disconnect
  "Disconnect the given connection"
  [^IConnection conn]
  (.close ^Driver (:driver conn)))

(defn create-session
  "Create a new session on the given Neo4J connection"
  ^ISession [^IConnection {:keys [driver opts]}]
  (assert (instance? Driver driver) "Neo4J driver not provided - check DB connection.")
  (if-let [database (:database opts)]
    (ISession. (.session ^Driver driver (SessionConfig/forDatabase database)) opts)
    (ISession. (.session ^Driver driver) opts)))

(defmacro with-session
  "Creates a session with the given name on the given connection and executes the body
  within the session.

  The session can be used with the given name in the rest of the body."
  [^IConnection conn session & body]
  `(with-open [~session (create-session ~conn)]
     ~@body))

(defn begin-transaction
  "Start a new transaction on the given Neo4J session"
  ^ITransaction [^ISession {:keys [session opts]}]
  (ITransaction. (.beginTransaction session) opts))

(defn commit!
  "Commits the given transaction"
  [^ITransaction {:keys [transaction]}]
  (.commit transaction))

(defn rollback
  "Rolls the given transaction back"
  [^ITransaction {:keys [transaction]}]
  (.rollback transaction))

(defmacro with-transaction
  "Create a transaction with given name on the given connection execute the body
  within the transaction.

  The transaction can be used with the given name in the rest of the body."
  [^IConnection conn trans & body]
  `(with-open [~trans (begin-transaction (create-session ~conn))]
     (try
       ~@body
       (catch Exception e#
         (rollback ~trans)
         (throw e#))
       (finally (commit! ~trans)))))

(defmulti execute!
  "Execute the given query on the specified connection with optional parameters"
  (fn [conn & args] (class conn)))

(defmethod execute! IConnection
  ([^IConnection conn ^String query]
   (execute! conn query {}))
  ([^IConnection conn ^String query ^clojure.lang.IPersistentMap params]
   (with-open [session (create-session conn)]
     (java-interop/execute (:session session) query params (:opts session)))))

(defmethod execute! ISession
  ([^ISession session ^String query]
   (java-interop/execute (:session session) query))
  ([^ISession session ^String query ^clojure.lang.IPersistentMap params]
   (java-interop/execute (:session session) query params (:opts session))))

(defmethod execute! ITransaction
  ([^ITransaction transaction ^String query]
   (java-interop/execute (:transaction transaction) query))
  ([^ITransaction transaction ^String query ^clojure.lang.IPersistentMap params]
   (java-interop/execute (:transaction transaction) query params (:opts transaction))))

(defmacro with-read-only-conn
  "Create a managed read transaction with the name given as runner-alias and execute the
  body within the transaction.

  The runner-alias given for the transaction can be used within the body."
  [^IConnection conn runner-alias & body]
  `(with-open [session# (create-session ~conn)]
     (.readTransaction
      (:session session#)
      (proxy [TransactionWork] []
        (execute [~runner-alias]
          ~@body)))))

(defn execute-read
  ([^IConnection conn ^String query]
   (execute-read conn query {}))
  ([^IConnection conn ^String query ^clojure.lang.IPersistentMap params]
   (with-read-only-conn conn tx
     (java-interop/execute tx query params (:opts conn)))))

(defmacro with-write-conn
  "Create a managed write transaction with the name given as runner-alias and execute the
  body within the transaction.

  The runner-alias given for the transaction can be used within the body."
  [^IConnection conn runner-alias & body]
  `(with-open [session# (create-session ~conn)]
     (.writeTransaction
      (:session session#)
      (proxy [TransactionWork] []
        (execute [~runner-alias]
          ~@body)))))

(defn execute-write!
  ([^IConnection conn ^String query]
   (execute-write! conn query {}))
  ([^IConnection conn ^String query ^clojure.lang.IPersistentMap params]
   (with-write-conn conn tx
     (java-interop/execute tx query params (:opts conn)))))

(defn create-index!
  "Creates an index on the given combination and properties"
  [runner alias label prop-keys]
  (execute! runner (builder/create-index-query alias label prop-keys)))

(defn drop-index!
  "Delete an index on the given combination and properties"
  [runner alias]
  (execute! runner (builder/drop-index-query alias)))

(defn create-from-builder!
  "Helper function to execute a specific query builder string and return the results"
  [runner ^clojure.lang.APersistentMap entity builder]
  (let [ref-id (or (:ref-id entity) (cypher/gen-ref-id))]
    (-> (execute! runner (builder (assoc entity :ref-id ref-id) true))
        first
        (get ref-id)
        (assoc :ref-id ref-id))))

(defn create-node!
  "Create a node based on the given Node representation"
  [runner node]
  (create-from-builder! runner node builder/create-node-query))

(defn create-rel!
  "Create a relationship based on the given Relationship representation"
  [runner rel]
  (create-from-builder! runner rel builder/create-rel-query))

(defn find-node
  "Takes a Node Lookup representation and returns a single matching node"
  [runner ^clojure.lang.APersistentMap {:keys [ref-id] :as node}]
  (->>
   (str (builder/lookup-node node true) " LIMIT 1")
   (execute! runner)
   (map #(get % ref-id))
   first))

(defn find-nodes
  "Takes a Node Lookup representation and returns all matching nodes"
  [runner ^clojure.lang.APersistentMap node]
  (->>
   (builder/lookup-node node true)
   (execute! runner)
   (map #(get % (:ref-id node)))))

(defn find-rel
  "Takes a Relationship representation and returns a single matching relationship"
  [runner ^clojure.lang.APersistentMap rel]
  (->>
   (str (builder/lookup-rel rel true) " LIMIT 1")
   (execute! runner)
   (map #(get % (:ref-id rel)))
   first))

(defn find-rels
  "Takes a Relationship representation and returns all matching relationships"
  [runner ^clojure.lang.APersistentMap rel]
  (->>
   (builder/lookup-rel rel true)
   (execute! runner)
   (map #(get % (:ref-id rel)))))

(defn create-graph!
  "Optimized function to create a whole graph within a transaction

  Format of the graph is:
  :lookups - collection of neo4j Node Lookup representations
  :nodes - collection of neo4j Node representations
  :rels  - collection of neo4j Relationship representations
  :returns - collection of aliases to return from query"
  [runner ^clojure.lang.APersistentMap graph]
  (execute! runner (builder/create-graph-query graph)))

(defn get-graph
  "Lookups the nodes based on given relationships and returns specified entities

  Format of the graph is:
  :nodes - collection of neo4j Node representations
  :rels  - collection of neo4j Relationship representations
  :returns - collection of aliases to return from query"
  [runner ^clojure.lang.APersistentMap graph]
  (execute! runner (builder/get-graph-query graph)))

(defn add-labels!
  "Takes a collection of labels and adds them to the found neo4j nodes"
  [runner
   ^clojure.lang.APersistentMap neo4j-node
   ^clojure.lang.APersistentVector labels]
  (execute! runner (builder/modify-labels-query "SET" neo4j-node labels)))

(defn remove-labels!
  "Takes a collection of labels and removes them from found neo4j nodes"
  [runner
   ^clojure.lang.APersistentMap neo4j-node
   ^clojure.lang.APersistentVector labels]
  (execute! runner (builder/modify-labels-query "REMOVE" neo4j-node labels)))

(defn update-props!
  "Takes a property map and updates the found neo4j objects with it based on the
  following rules:

  Keys existing only in the given property map is added to the object
  Keys existing only in the property map on the found object is kept as is
  Keys existing in both property maps are updated with values from the given property map"
  [runner
   ^clojure.lang.APersistentMap neo4j-entity
   ^clojure.lang.APersistentMap props]
  (execute! runner (builder/modify-properties-query "+=" neo4j-entity props)))

(defn replace-props!
  "Takes a property map and replaces the properties on all found neo4j objects with it"
  [runner
   ^clojure.lang.APersistentMap neo4j-entity
   ^clojure.lang.APersistentMap props]
  (execute! runner (builder/modify-properties-query "=" neo4j-entity props)))

(defn delete-node!
  "Takes a neo4j node representation and deletes nodes found based on it"
  [runner ^clojure.lang.APersistentMap neo4j-node]
  (execute! runner (builder/delete-node neo4j-node)))

(defn delete-rel!
  "Takes a neo4j relationship representation and deletes relationships found based on it"
  [runner ^clojure.lang.APersistentMap neo4j-rel]
  (execute! runner (builder/delete-rel neo4j-rel)))

(defn create-query
  "Takes a cypher query as input and returns a anonymous function that
  takes a query runner and return the query result as a map.

  The function can also take a optional map of parameters used to replace params in the query string.

  This functions can be used together with parameters to ensure better cached queries in Neo4j."
  [query]
  (fn
    ([runner] (execute! runner query))
    ([runner params] (execute! runner query params))))
