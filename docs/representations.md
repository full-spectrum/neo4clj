# Clojure representations

## Entity identification

In Neo4clj there are two different identification forms `ref-id` and `id`.

The `ref-id` is used to identify a entity in a cypher query and allows the use of the same entity in other parts of the query.

Meanwhile the `id` is used to represent an actual entity in the Neo4j database and can be used in fetches to lookup specific entities.
The `id` will always be set when an entity is fetched from the Neo4j database.

A entity can have both an `id` and a `ref-id`, but only entities actually persisted in Neo4j will have the `id`.

In functions where the entity can be reffered by other entities like the `create-graph!` or `get-graph` functions you always need
to manually set a `ref-id`, while in other functions like `create-node!` and `add-labels!` it is optional and the Neo4clj client
will automatically create one if it is missing.

## Style guides and conversions

To ensure the use of Neo4clj feels like an integrated part of Clojure, we have added conversions between the Clojure
representations and there Neo4j equivalents. This allows you to use Clojure keywords for labels and property keys.

When a Clojure representation is converted to its Cypher equivalent it will be converted based on the Cypher style guide
and vice versa when a Cypher entity is retrieved it is converted to its Clojure equivalent based on the Clojure style guide.

Please notice, if you write manual Cypher queries you need to use the Cypher style guide to determine the actual keys and labels.
As an example in Clojure the property key for first name would be `:first-name` in Cypher it would be `firstName` and the
label `:person` in Clojure would be `PERSON` in Cypher.

## Node

~~~clojure
{:id       34                            ;; The id from Neo4J, this is only set if object is fetched from Neo4J
 :ref-id   "G__123"                      ;; The variable name used for this entity in bolt queries
 :labels   [:example-node :first-level]  ;; Collection of labels to associated with the entity (optional)
 :props    {:property-1 123              ;; Map of properties on the node. Nesting is not supported (optional)
            :property-2 "something"}}
~~~

## Relationship

~~~clojure
{:id       12                            ;; The id from Neo4J, this is only set if object is fetched from Neo4J
 :ref-id   "G__321"                      ;; The variable name used for this entity in bolt queries
 :type     :example-relationship         ;; The type of the relationship
 :from     Node                          ;; The node representation of the start of the relationship
 :to       Node                          ;; The node representation of the end of the relationship
 :props    {:property-1 123              ;; Map of properties on the relationship. Nesting is not supported (optional)
            :property-2 "something"}}
~~~

## Lookup

### By id

~~~clojure
{:ref-id   "G__312"                      ;; The variable name used for this entity in bolt queries
 :id       12}                           ;; The id to lookup the entity in Neo4J
~~~

### By labels

~~~clojure
{:ref-id   "G__312"                      ;; The variable name used for this entity in bolt queries
 :labels   [:example-node]}              ;; Collection of labels required to be a match
~~~

### By properties

~~~clojure
{:ref-id   "G__312"                      ;; The variable name used for this entity in bolt queries
 :props    {:property-1 123              ;; Map of properties required to be a match
            :property-2 "something"}}
~~~

### By labels and properties

~~~clojure
{:ref-id   "G__312"                      ;; The variable name used for this entity in bolt queries
 :labels   [:example-node :first-level]  ;; Collection of labels required to be a match
 :props    {:property-1 123              ;; Map of properties required to be a match
            :property-2 "something"}}
~~~

### By one of multiple properties

~~~clojure
{:ref-id   "G__312"                      ;; The variable name used for this entity in bolt queries
 :props    [{:property-1 123             ;; Map of properties required to be a match
             :property-2 "something"}
            {:property-1 321
             :property-2 "something else"}]}
~~~

### By labels and one of multiple properties

~~~clojure
{:ref-id   "G__312"                      ;; The variable name used for this entity in bolt queries
 :labels   [:example-node :first-level]  ;; Collection of labels required to be a match
 :props    [{:property-1 123             ;; Collection of property maps, where one is required to be a match
             :property-2 "something"}
             {:property-1 321
              :property-2 "something else"}]}
~~~

## Create graph structure

~~~clojure
{:lookups   [Lookup]               ;; Collection of Lookup representations
 :nodes     [Node]                 ;; Collection of Node representations
 :rels      [Relationship]         ;; Collection of Relationship representations
 :returns   ["G__123" "G__321"]    ;; Collection of reference-ids to return
~~~

In addition to the normal Relationship representation, it is possible to use the ref-id's associated with the
nodes in :lookups and :nodes directly in the :from and :to key.

## Get graph structure

~~~clojure
{:nodes     [Node]                 ;; Collection of Node representations
 :rels      [Relationship]         ;; Collection of Relationship representations
 :returns   ["G__123" "G__321"]    ;; Collection of reference-ids to return
~~~

In addition to the normal Relationship representation, it is possible to use the ref-id's associated with the
nodes in :lookups and :nodes directly in the :from and :to key. Also in addition the :from and :to keys are optional,
which allows the query to match any node. It is also possible to use the additional key :exists, which take a boolean
as value, to represent non-existent relationships.
