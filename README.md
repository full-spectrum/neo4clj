# Neo4J Clojure client

Neo4clj is a idomatic clojure client, exclusivly using [Bolt](https://boltprotocol.org) for performance.

## Installation

Add the following dependency to `project.clj`:

```
[fullspectrum/neo4clj "1.0.0-ALPHA5"]
```

[![Clojars Project](https://img.shields.io/clojars/v/fullspectrum/neo4clj.svg)](https://clojars.org/fullspectrum/neo4clj)


# Getting started

~~~clojure
(require '[neo4clj.client :as client])

;; Create a connection to the Neo4j server
(def connection
  (client/connect "bolt://localhost:7687" "neo4j" "password"))

;; Create a new node on the connected server and return it
(client/create-node
  connection
  {:ref-id "N"
   :labels [:person]
   :props {:first-name "Thomas"
           :last-name "Anderson"}})

;; Close the connection to the Neo4J server
(client/disconnect connection)
~~~

# Clojure representations

Neo4clj uses Clojure maps to represent Nodes and Relationships.
To learn more please see [Clojure Representations](docs/representations.md)

# Examples

To learn more about how to use Neo4clj please take a look at our [examples](docs/examples.md)

# Supported Features

Neo4clj supports the following features via the Bolt Protocol:

* [Cypher queries](http://docs.neo4j.org/chunked/stable/cypher-query-lang.html)
* Sessions
* Transactions

Neo4clj also supports the following operations through idomatic functions:

Create, read, update and delete nodes
Create, read, update and delete relationships
Create and read a complete graph
Create and delete indexes


## Version matrix

| Neo4clj        | Clojure | `neo-java-driver` | Neo4j Server |
| -------------- | ------- | ----------------- | ------------ |
| 1.0.0-SNAPSHOT |  1.10.0 |             1.7.2 |        3.5.x |
| 1.0.0-ALPHA1   |  1.10.0 |             4.2.0 |        4.2.x |
| 1.0.0-ALPHA2   |  1.10.0 |             4.2.0 |        4.2.x |
| 1.0.0-ALPHA3   |  1.10.0 |             4.2.0 |        4.2.x |
| 1.0.0-ALPHA4   |  1.10.0 |             4.2.0 |        4.2.x |
| 1.0.0-ALPHA5   |  1.10.0 |             4.2.0 |        4.2.x |
| 1.0.0-ALPHA6   |  1.10.3 |             4.2.5 |        4.2.x |


## Acknowledgements

This project has been inspired by the work of two other projects listed below.

Neocons by Michael Klishin (https://github.com/michaelklishin/neocons)
neo4j-clj by Christian Betz (https://github.com/gorillalabs/neo4j-clj)


## License

Copyright (C) 2021 Claus Engel-Christensen, Jacob Emcken, and the Full Spectrum team.

Licensed under the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html)
