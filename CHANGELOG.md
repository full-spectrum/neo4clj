# Change Log
All notable changes to this project will be documented in this file.

## Changes between Neo4clj 1.0.0-SNAPSHOT and 1.0.0

### Repo changed from single to multi, using lein sub

### Node and Relationship create functions

The returned hash-map representations of the create-node! and create-relationship! functions now includes the given ref-id. If no ref-id was set on the hash-map given to the function, an ref-id is auto-generated and the auto-generated ref-id is returned.
