# Clojure representations

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
{:lookups         [Lookup]               ;; Collection of Lookup representations
 :nodes           [Node]                 ;; Collection of Node representations
 :relationships   [Relationship]         ;; Collection of Relationship representations
 :returns         ["G__123" "G__321"]    ;; Collection of reference-ids to return
~~~
