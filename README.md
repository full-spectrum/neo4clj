# Neo4J Clojure client

Neo4clj is a idomatic clojure client, exclusivly using [Bolt][] for performance.

Add the following dependency to `project.clj`:

```
[fullspectrum/neo4clj "1.0.0"]
```

[Bolt]: https://boltprotocol.org


# Getting started

~~~clojure
(require '[full-spectrum.neo4clj.client :as client])

;; Create a connection to the Neo4j server
(def connection
  (client/connect {:url "bolt://localhost:7687"
                   :username "neo4j"
                   :password "password"}))

;; Create a new node on the connected server and return it
(client/create-node
  connection
  {:ref-id "N"
   :labels [:person]
   :properties {:first-name "Thomas"
                :last-name "Anderson"}})

;; Close the connection to the Neo4J server
(client/disconnect connection)
~~~

# Clojure representations

## Node

~~~clojure
{:id       34                            ;; The id from Neo4J, this is only set if object is fetched from Neo4J
 :ref-id   "G__123"                      ;; The variable name used for this entity in bolt queries
 :labels   [:example-node :first-level]  ;; Collection of labels to associated with the entity
 :props    {:property-1 123              ;; Map of properties on the node. Nesting is not supported
            :property-2 "something"}}
~~~


## Relationship

~~~clojure
{:id       12                            ;; The id from Neo4J, this is only set if object is fetched from Neo4J
 :ref-id   "G__321"                      ;; The variable name used for this entity in bolt queries
 :type     :example-relationship         ;; The type of the relationship
 :from     Node                          ;; The node representation of the start of the relationship
 :to       Node                          ;; The node representation of the end of the relationship
 :props    {:property-1 123              ;; Map of properties on the relationship. Nesting is not supported
            :property-2 "something"}}
~~~


## Lookup

### By id

~~~clojure
{:ref-id   "G__312"                      ;; The variable name used for this entity in bolt queries
 :id       12}                           ;; The id to lookup the entity in Neo4J
~~~

### By labels and properties

#### By labels and specific property map

~~~clojure
{:ref-id   "G__312"                      ;; The variable name used for this entity in bolt queries
 :labels   [:example-node :first-level]  ;; Collection of labels required to be a match
 :props    {:property-1 123              ;; Map of properties required to be a match
            :property-2 "something"}}
~~~

#### By labels and one of multiple property maps

~~~clojure
{:ref-id   "G__312"                      ;; The variable name used for this entity in bolt queries
 :labels   [:example-node :first-level]  ;; Collection of labels required to be a match
 :props    [{:property-1 123             ;; Collection of property maps, where one is required to be a match
             :property-2 "something"}
             {:property-1 321
              :property-2 "something else"}]}
~~~

## Graph

~~~clojure
{:lookups         [Lookup]               ;; Collection of Lookup representations
 :nodes           [Node]                 ;; Collection of Node representations
 :relationships   [Relationship]         ;; Collection of Relationship representations
 :return-aliases  ["G__123" "G__321"]    ;; Collection of reference-ids to return
~~~


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


## Version matrix

| Neo4clj  | Clojure | `neo-java-driver` | Neo4j Server |
| -------- | ------- | ----------------- | ------------ |
|   1.0.0  |  1.10.0 |             1.7.2 |        3.5.x |


## License

Copyright (C) 2019 Claus Engel-Christensen, Jacob Emcken, and the Full Spectrum team.

Licensed under the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html)
