(ns full-spectrum.neo4clj.client
  (:require [clojure.string :as str]
            [full-spectrum.neo4clj.internal.neo4j :as neo4j]
            [full-spectrum.neo4clj.internal.query-builder :as builder])
  (:import  [org.neo4j.driver.v1 StatementRunner]))

(defn connect
  "Connect to the given neo4j server using bolt"
  ^StatementRunner [{:keys [url usr pwd opts]}]
  (if (or (nil? usr) (nil? pwd))
    (if (nil? opts)
      (neo4j/connect url)
      (neo4j/connect url (neo4j/build-config opts)))
    (if (nil? opts)
      (neo4j/connect url usr pwd)
      (neo4j/connect url usr pwd (neo4j/build-config opts)))))

(defn disconnect
  "Disconnect the given connection"
  [^StatementRunner conn]
  (neo4j/disconnect conn))

(defn execute-multiple!
  "Execute the given queries on the specified connection within the same session"
  [conn & queries]
  (with-open [session (neo4j/create-session conn)]
    (for [query queries]
      (let [query-string (if (string? query) query (first query))
            params (if (coll? query) (second query) {})]
        (neo4j/execute session query-string params)))))

(defn execute!
  "Execute the given query on the specified connection"
  ([^StatementRunner conn ^String query]
   (execute! conn query {}))
  ([^StatementRunner conn ^String query ^clojure.lang.IPersistentMap params]
   (with-open [session (neo4j/create-session conn)]
     (neo4j/execute conn query params))))

(defn execute-in-transaction
  "Execute the given queries on the specified connection in a transaction"
  [^StatementRunner conn & queries]
  (with-open [trans (neo4j/begin-transaction (neo4j/create-session conn))]
    (try
      (let [result (execute-multiple! trans queries)]
        (neo4j/commit-transaction trans)
        result)
      (catch Exception e
        (neo4j/rollback-transaction trans)
        (throw e)))))

(defmacro execute-in-transaction2
  [^StatementRunner conn trans & body]
   `(with-open [~trans (neo4j/begin-transaction (neo4j/create-session ~conn))]
     ~@body))

(defn create-index!
  [^StatementRunner conn label prop-key]
  (execute! conn (builder/index-query "CREATE" label prop-key)))

(defn drop-index!
  [^StatementRunner conn label prop-key]
  (execute! conn (builder/index-query "DROP" label prop-key)))

(defn create-node!
  [^StatementRunner conn ^clojure.lang.APersistentMap node]
  (get (first (execute! conn (builder/create-node-query node true)))
       (:ref-id node)))

(defn find-nodes!
  [^StatementRunner conn ^clojure.lang.APersistentMap node]
  (map #(get % (:ref-id node))
       (execute! conn (builder/lookup-query node true))))

(defn create-relationship!
  [^StatementRunner conn ^clojure.lang.APersistentMap rel]
  (execute! conn (builder/create-relationship-query rel true)))

(defn create-graph!
  "Optimized function to create a whole graph within a transaction

  Format of the graph is:
  :lookups - collection of neo4j lookup representations
  :nodes - collection of neo4j node representations
  :relationships  - collection of neo4j relationship representations
  :return-aliases - collection of aliases to return from query"
  [^StatementRunner conn ^clojure.lang.APersistentMap graph]
  (execute! conn (builder/create-graph-query graph)))

(defn add-labels!
  "Takes a collection of labels and adds them to the found neo4j entities"
  [^StatementRunner conn ^clojure.lang.APersistentMap neo4j-entity ^clojure.lang.APersistentVector labels]
  (execute! conn (builder/modify-labels-query "SET" neo4j-entity labels)))

(defn remove-labels!
  "Takes a collection of labels and removes them from found neo4j objects"
  [^StatementRunner conn ^clojure.lang.APersistentMap neo4j-entity ^clojure.lang.APersistentVector labels]
  (execute! conn (builder/modify-labels-query "REMOVE" neo4j-entity labels)))

(defn update-properties!
  "Takes a property map and updates the found neo4j objects with it based on the
  following rules:

  Keys only existing in the given property map is added to the object
  Keys only existing on the found object is kept as is
  Keys found in both are updated with values from the given property map"
  [^StatementRunner conn ^clojure.lang.APersistentMap neo4j-entity ^clojure.lang.APersistentMap props]
  (execute! conn (builder/modify-properties-query "+=" neo4j-entity props)))

(defn replace-properties!
  "Takes a property map and replaces the properties on all found neo4j objects with it"
  [^StatementRunner conn ^clojure.lang.APersistentMap neo4j-entity ^clojure.lang.APersistentMap props]
  (execute! conn (builder/modify-properties-query "=" neo4j-entity props)))

(defn delete!
  "Takes a neo4j representation and deletes objects found based on it"
  [^StatementRunner conn ^clojure.lang.APersistentMap neo4j-entity]
  (execute! conn (builder/delete-query neo4j-entity)))
