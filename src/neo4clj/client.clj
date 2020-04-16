(ns neo4clj.client
  (:require [clojure.string :as str]
            [neo4clj.cypher :as cypher]
            [neo4clj.java-interop :as java-interop]
            [neo4clj.query-builder :as builder])
  (:import  [org.neo4j.driver Driver Session QueryRunner Transaction]))

(defn connect
  "Connect through bolt to the given neo4j server

  Supports the current options:
  :log :level [:all :error :warn :info :off] - defaults to :warn
  :encryption [:required :none] - defaults to :required"
  (^Driver [^String url]
   (java-interop/connect url))
  (^Driver [^String url ^clojure.lang.IPersistentMap opts]
   (java-interop/connect url (java-interop/build-config opts)))
  (^Driver [^String url ^String usr ^String pwd]
   (java-interop/connect url usr pwd))
  (^Driver [^String url ^String usr ^String pwd ^clojure.lang.IPersistentMap opts]
   (java-interop/connect url usr pwd (java-interop/build-config opts))))

(defn disconnect
  "Disconnect the given connection"
  [^Driver conn]
  (.close conn))

(defn create-session
  "Create a new session on the given Neo4J connection"
  ^Session [^Driver conn]
  (.session conn))

(defmacro with-session
  "Creates a session with the given name on the given connection and executes the body
  within the session.

  The session can be used with the given name in the rest of the body."
  [^Driver conn session & body]
  `(with-open [~session (create-session ~conn)]
        ~@body))

(defn begin-transaction
  "Start a new transaction on the given Neo4J session"
  ^Transaction [^Session session]
  (.beginTransaction session))

(defn commit!
  "Commits the given transaction"
  [^Transaction trans]
  (.success trans))

(defn rollback
  "Rolls the given transaction back"
  [^Transaction trans]
  (.failure trans))

(defmacro with-transaction
  "Create a transaction with given name on the given connection execute the body
  within the transaction.

  The transaction can be used with the given name in the rest of the body."
  [^Driver conn trans & body]
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

(defmethod execute! Driver
  ([^Driver conn ^String query]
   (execute! conn query {}))
  ([^Driver conn ^String query ^clojure.lang.IPersistentMap params]
   (with-open [session (create-session conn)]
     (java-interop/execute session query params))))

(defmethod execute! QueryRunner
  ([^QueryRunner runner ^String query]
   (java-interop/execute runner query))
  ([^QueryRunner runner ^String query ^clojure.lang.IPersistentMap params]
   (java-interop/execute runner query params)))

(defn create-index!
  [conn label prop-keys]
  (execute! conn (builder/index-query "CREATE" label prop-keys)))

(defn drop-index!
  [conn label prop-keys]
  (execute! conn (builder/index-query "DROP" label prop-keys)))

(defn create-from-builder!
  [conn ^clojure.lang.APersistentMap entity builder]
  (let [ref-id (or (:ref-id entity) (cypher/gen-ref-id))]
    (-> (execute! conn (builder (assoc entity :ref-id ref-id) true))
        first
        (get ref-id)
        (assoc :ref-id ref-id))))


(defn create-node!
  [conn node]
  (create-from-builder! conn node builder/create-node-query))

(defn create-relationship!
  [conn rel]
  (create-from-builder! conn rel builder/create-relationship-query))

(defn find-nodes!
  [conn ^clojure.lang.APersistentMap node]
  (map #(get % (:ref-id node))
       (execute! conn (builder/lookup-query node true))))

(defn create-graph!
  "Optimized function to create a whole graph within a transaction

  Format of the graph is:
  :lookups - collection of neo4j lookup representations
  :nodes - collection of neo4j node representations
  :relationships  - collection of neo4j relationship representations
  :returns - collection of aliases to return from query"
  [conn ^clojure.lang.APersistentMap graph]
  (execute! conn (builder/create-graph-query graph)))

(defn get-graph
  "Lookups the nodes based on given relationships and returns specified entities

  Format of the graph is:
  :nodes - collection of neo4j node representations
  :relationships  - collection of neo4j relationship representations
  :return-aliases - collection of aliases to return from query
  :unique-by - reference-id to use for creating a list of distinct nodes"
  [conn ^clojure.lang.APersistentMap graph]
  (execute! conn (builder/get-graph-query graph)))

(defn add-labels!
  "Takes a collection of labels and adds them to the found neo4j nodes"
  [conn
   ^clojure.lang.APersistentMap neo4j-node
   ^clojure.lang.APersistentVector labels]
  (execute! conn (builder/modify-labels-query "SET" neo4j-node labels)))

(defn remove-labels!
  "Takes a collection of labels and removes them from found neo4j nodes"
  [conn
   ^clojure.lang.APersistentMap neo4j-node
   ^clojure.lang.APersistentVector labels]
  (execute! conn (builder/modify-labels-query "REMOVE" neo4j-node labels)))

(defn update-props!
  "Takes a property map and updates the found neo4j objects with it based on the
  following rules:

  Keys existing only in the given property map is added to the object
  Keys existing only in the property map on the found object is kept as is
  Keys existing in both property maps are updated with values from the given property map"
  [conn
   ^clojure.lang.APersistentMap neo4j-entity
   ^clojure.lang.APersistentMap props]
  (execute! conn (builder/modify-properties-query "+=" neo4j-entity props)))

(defn replace-props!
  "Takes a property map and replaces the properties on all found neo4j objects with it"
  [conn
   ^clojure.lang.APersistentMap neo4j-entity
   ^clojure.lang.APersistentMap props]
  (execute! conn (builder/modify-properties-query "=" neo4j-entity props)))

(defn delete!
  "Takes a neo4j representation and deletes objects found based on it"
  [conn ^clojure.lang.APersistentMap neo4j-entity]
  (execute! conn (builder/delete-query neo4j-entity)))
