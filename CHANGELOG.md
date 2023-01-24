# Change Log
All notable changes to this project will be documented in this file.

## 1.1.0 (2023-01-24)

* Updated core and test to use Neo4J 5.3.0 Java driver

## 1.0.1 (2022-11-29)

* Updated syntax for the with-db test-util function
* Add support for alternative port and host for the in-memory database

## 1.0.0 (2022-10-04)

* Update Neo4j driver to version 4.4.9
* Updated Clojure to version 1.11.1
* Updated Camel-Snake-Kebab to version 0.4.3
* Updated Clojure Java time to version 1.1.0

## Changes between Neo4clj 1.0.0-SNAPSHOT and 1.0.0

* Repo changed from single to multi, using lein sub
* Node and Relationship create functions
* The returned hash-map representations of the create-node! and create-relationship! functions now includes the given ref-id. If no ref-id was set on the hash-map given to the function, an ref-id is auto-generated and the auto-generated ref-id is returned.
