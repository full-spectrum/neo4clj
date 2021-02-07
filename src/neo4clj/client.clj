(ns neo4clj.client
  (:require [clojure.string :as str]
            [neo4clj.cypher :as cypher]
            [neo4clj.java-interop :as java-interop]
            [neo4clj.query-builder :as builder])
  (:import  [org.neo4j.driver Driver Session QueryRunner SessionConfig Transaction TransactionWork]))

(defrecord Connection [^Driver driver ^String database])

(defn connect
  "Connect through bolt to the given neo4j server

  Supports the current options:
  :log :level [:all :error :warn :info :off] - defaults to :warn
  :encryption [:required :none] - defaults to :required
  :database - defaults to nil"
  (^Connection [^String url]
   (Connection. (java-interop/connect url) nil))
  (^Connection [^String url ^clojure.lang.IPersistentMap opts]
   (Connection. (java-interop/connect url (java-interop/build-config opts)) (:database opts)))
  (^Connection [^String url ^String usr ^String pwd]
   (Connection. (java-interop/connect url usr pwd) nil))
  (^Connection [^String url ^String usr ^String pwd ^clojure.lang.IPersistentMap opts]
   (Connection. (java-interop/connect url usr pwd (java-interop/build-config opts)) (:database opts))))

(defn disconnect
  "Disconnect the given connection"
  [^Connection conn]
  (.close (:driver conn)))

(defn create-session
  "Create a new session on the given Neo4J connection"
  (^Session [^Connection {:keys [driver database] :as conn}]
   (if database
     (.session driver (SessionConfig/forDatabase database))
     (.session driver))))

(defmacro with-session
  "Creates a session with the given name on the given connection and executes the body
  within the session.

  The session can be used with the given name in the rest of the body."
  [^Connection conn session & body]
  `(with-open [~session (create-session ~conn)]
        ~@body))

(defn begin-transaction
  "Start a new transaction on the given Neo4J session"
  ^Transaction [^Session session]
  (.beginTransaction session))

(defn commit!
  "Commits the given transaction"
  [^Transaction trans]
  (.commit trans))

(defn rollback
  "Rolls the given transaction back"
  [^Transaction trans]
  (.rollback trans))

(defmacro with-transaction
  "Create a transaction with given name on the given connection execute the body
  within the transaction.

  The transaction can be used with the given name in the rest of the body."
  [^Connection conn trans & body]
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

(defmethod execute! Connection
  ([^Connection conn ^String query]
   (execute! conn query {}))
  ([^Connection conn ^String query ^clojure.lang.IPersistentMap params]
   (with-open [session (create-session conn)]
     (java-interop/execute session query params))))

(defmethod execute! QueryRunner
  ([^QueryRunner runner ^String query]
   (java-interop/execute runner query))
  ([^QueryRunner runner ^String query ^clojure.lang.IPersistentMap params]
   (java-interop/execute runner query params)))

(defmacro with-read-conn
  "Create a managed read transaction with the name given as runner-alias and execute the
  body within the transaction.

  The runner-alias given for the transaction can be used within the body."
  [^Connection conn runner-alias & body]
  `(with-open [session# (create-session ~conn)]
     (.readTransaction
      session#
      (proxy [TransactionWork] []
        (execute [~runner-alias]
          ~@body)))))

(defmacro with-write-conn
  "Create a managed write transaction with the name given as runner-alias and execute the
  body within the transaction.

  The runner-alias given for the transaction can be used within the body."
  [^Connection conn runner-alias & body]
  `(with-open [session# (create-session ~conn)]
     (.writeTransaction
      session#
      (proxy [TransactionWork] []
        (execute [~runner-alias]
          ~@body)))))

(defn create-index!
  "Creates an index on the given combination and properties"
  [runner label prop-keys]
  (execute! runner (builder/index-query "CREATE" label prop-keys)))

(defn drop-index!
  "Delete an index on the given combination and properties"
  [runner label prop-keys]
  (execute! runner (builder/index-query "DROP" label prop-keys)))

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
  "Takes a Node representation and returns a single matching node"
  [runner ^clojure.lang.APersistentMap node]
  (->>
  (str (builder/lookup-node node true) " LIMIT 1")
  (execute! runner)
  (map #(get % (:ref-id node)))
  first))

(defn find-nodes
  "Takes a Node representation and returns all matching nodes"
  [runner ^clojure.lang.APersistentMap node]
  (map #(get % (:ref-id node))
       (execute! runner (builder/lookup-node node true))))

(defn find-rel
  "Takes a Relationship representation and returns a single matching relationship"
  [runner ^clojure.lang.APersistentMap rel]
  (first (map #(get % (:ref-id rel))
              (execute! runner (str (builder/lookup-rel rel true) " LIMIT 1")))))

(defn find-rels
  "Takes a Relationship representation and returns all matching relationships"
  [runner ^clojure.lang.APersistentMap rel]
  (map #(get % (:ref-id rel))
       (execute! runner (builder/lookup-rel rel true))))

(defn create-graph!
  "Optimized function to create a whole graph within a transaction

  Format of the graph is:
  :lookups - collection of neo4j lookup representations
  :nodes - collection of neo4j node representations
  :rels  - collection of neo4j relationship representations
  :returns - collection of aliases to return from query"
  [runner ^clojure.lang.APersistentMap graph]
  (execute! runner (builder/create-graph-query graph)))

(defn get-graph
  "Lookups the nodes based on given relationships and returns specified entities

  Format of the graph is:
  :nodes - collection of neo4j node representations
  :rels  - collection of neo4j relationship representations
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
