# Neo4clj, a Clojure client for the Neo4J

Neo4clj is a idomatic clojure client.

# Getting started

~~~
(require '[full-spectrum.neo4clj.client :as client])

;; Create a connection to the Neo4j server
(def connection
  (client/connect {:url "bolt://localhost:7687"
                   :username "neo4j"
                   :password "password"}))

;; Create a new node on the connected server and return it
(client/create-node
  connection
  {:reference-id "N"
   :labels [:person]
   :properties {:first-name "Tim"
                :last-name "Anderson"}})

;; Close the connection to the Neo4J server
(client/disconnect connection)
~~~

# Clojure representations

## Node

~~~~
{:id       34                            ;; The id from Neo4J, this is only set if object is fetched from Neo4J
 :ref-id   "G_123"                       ;; The variable name used for this entity in bolt queries
 :labels   [:example-node :first-level]  ;; Collection of labels to associated with the entity
 :props    {:property-1 123              ;; Map of properties on the node. Nesting is not supported
            :property-2 "something"}}
~~~~

## Relationship

~~~~
{:id        12                            ;; The id from Neo4J, this is only set if object is fetched from Neo4J
 :ref-id    "G_321"                       ;; The variable name used for this entity in bolt queries
 :type      :example-relationship         ;; The type of the relationship
 :from      Node                          ;; The node representation of the start of the relationship
 :to        Node                          ;; The node representation of the end of the relationship
 :props     {:property-1 123              ;; Map of properties on the relationship. Nesting is not supported
             :property-2 "something"}}
~~~~

## Lookup

### By id

~~~~
{:ref-id    "G312"                        ;; The variable name used for this entity in bolt queries
 :id        12}                           ;; The id to lookup the entity in Neo4J
~~~~

### By labels and properties

#### By labels and specific property map

~~~~
{:ref-id   "G_312"                       ;; The variable name used for this entity in bolt queries
 :labels   [:example-node :first-level]  ;; Collection of labels required to be a match
 :props    {:property-1 123              ;; Map of properties required to be a match
            :property-2 "something"}}
~~~~

#### By labels and one of multiple property maps

~~~~
{:ref-id   "G_312"                       ;; The variable name used for this entity in bolt queries
 :labels   [:example-node :first-level]  ;; Collection of labels required to be a match
 :props    [{:property-1 123             ;; Collection of property maps, where one is required to be a match
             :property-2 "something"}
             {:property-1 321
              :property-2 "something else"}]}
~~~~

## Graph

~~~~
{:lookups         [Lookup]                    ;; Collection of Lookup representations
 :nodes           [Node]                      ;; Collection of Node representations
 :relationships   [Relationship]              ;; Collection of Relationship representations
 :return-aliases  ["G_123" "G_321"]           ;; Collection of reference-ids to return
~~~~

# Supported Features

Neo4clj supports the following features via the Bolt Protocol:

* [Cypher queries](http://docs.neo4j.org/chunked/stable/cypher-query-lang.html)
* Sessions
* Transactions
* Full support through current Neo4J java driver

Neo4clj also supports the following operations through idomatic functions:

Create, read, update and delete nodes
Create, read, update and delete relationships
Create and read a complete graph
Create and delete indexes

## Supported Clojure Versions

Neocons requires Clojure 1.8+.

## Supported Neo4J Server Versions

### Neocons 3.2

Neo4clj `1.0` targets Neo4j Server 3.5.x  and includes Neo4j's Bolt Protocol.

## License

Copyright (C) 2019 Claus Engel-Christensen, Jacob Emcken, and the Full Spectrum IVS team.

Licensed under the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html)