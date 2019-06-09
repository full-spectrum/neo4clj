(ns neo4clj.query-builder
  (:require [clojure.string :as str]
            [neo4clj.cypher :as cypher]
            [neo4clj.sanitize :as sanitize]))

(defn create-node-query
  "Returns the bolt query to create a node based on the given node representation"
  [{:keys [ref-id] :as node} return?]
  (str "CREATE "
       (first (cypher/node node))
       (when return? (str " RETURN " ref-id))))

(defn lookup-query
  "Takes a lookup representation and generates a bolt query

  A lookup representation needs the :reference.id to be set and
  either the :id or :labels and :properties keys"
  [{:keys [ref-id] :as node} return?]
  (let [cypher-node (cypher/node node)]
    (str "MATCH "
         (str (first cypher-node)
              (when (second cypher-node)
                (str " WHERE " (second cypher-node))))
         (when return? (str " RETURN " ref-id)))))

(defn index-query
  "Creates a query to modify index, allowed operations are: CREATE, DROP"
  [operation label prop-key]
  (str operation " INDEX ON " (sanitize/cypher-label label) "("
        (sanitize/cypher-property-key prop-key) ")"))

(defn lookup-non-referred-node [ref-id node]
  "Creates a query to lookup a node without a ref-id and refers it as given ref-id"
  (when-not (:ref-id node)
    (str (lookup-query
          (assoc node :ref-id ref-id)
          false)
         " ")))

(defn create-relationship-query
  "Returns the bolt query to create a one directional relationship
  based on the given relationship representation"
  [{:keys [ref-id from to] :as rel} return?]
  (let [from-ref-id (or (:ref-id from) (cypher/gen-ref-id))
        to-ref-id (or (:ref-id to) (cypher/gen-ref-id))]
    (str (lookup-non-referred-node from-ref-id from)
         (lookup-non-referred-node to-ref-id to)
         "CREATE "
         (cypher/relationship from-ref-id to-ref-id rel)
         (when return? (str " RETURN " ref-id)))))

(defn create-graph-query
  "Takes a graph representation and creates the nodes and relationship defined
  and returns any aliases specified in the representation"
  [{:keys [lookups nodes relationships returns]}]
  (str/join " " (concat
                 (map #(lookup-query % false) lookups)
                 (map #(create-node-query % false) nodes)
                 (map #(create-relationship-query % false) relationships)
                 (when-not (empty? returns)
                   (vector (str "RETURN " (str/join "," returns)))))))


(defn modify-labels-query
  "Takes a operation and a neo4j object representation, along with a collection
  of labels and either sets or removes them

  Allowed operations are: SET, REMOVE"
  [operation {:keys [ref-id] :as entity} labels]
  (str (lookup-query entity false) " " operation " " ref-id (cypher/labels labels)))

(defn modify-properties-query
  "Takes a neo4j entity representation, along with a properties map

  Allowed operations are: =, +="
  [operation {:keys [ref-id] :as neo4j-entity} props]
  (str (lookup-query neo4j-entity false) " SET "
       ref-id " " operation (cypher/properties props)))

(defn delete-query
  "Takes a neo4j entity representation and deletes it"
  [{:keys [ref-id] :as neo4j-entity}]
  (str (lookup-query neo4j-entity false) " DELETE " ref-id))
