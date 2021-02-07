# Examples

Below we have given examples of the most common operations in Neo4clj.

## Table of Contents

- [Basic requirements](#basic-requirements)
- [Connect to neo4j server](#connect-to-neo4j-server)
  * [Basic connection](#basic-connection)
  * [Authenticated connection](#authenticated-connection)
  * [Connection with options](#connection-with-options)
- [Disconnect from neo4j server](#disconnect-from-neo4j-server)
- [Execute queries](#execute-queries)
  * [Execute query without parameters](#execute-query-without-parameters)
  * [Execute query with parameters](#execute-query-with-parameters)
- [Node CRUD](#node-crud)
  * [Create a Node](#create-a-node)
  * [Find nodes in Neo4j](#find-nodes-in-neo4j)
  * [Update and Delete Node](#update-and-delete-node)
- [Relationship CRUD](#relationship-crud)
  * [Create a Relationship](#create-a-relationship)
  * [Find relationships in Neo4j](#find-relationships-in-neo4j)
  * [Update and Delete Relationship](#update-and-delete-relationship)
- [Update and Delete Neo4j Entity](#update-and-delete-neo4j-entity)
  * [Update Node (Labels)](#update-node--labels-)
    + [Add labels](#add-labels)
    + [Remove labels](#remove-labels)
  * [Update Node or Relationship (properties)](#update-node-or-relationship--properties-)
    + [Update properties](#update-properties)
    + [Replace properties](#replace-properties)
  * [Delete Node or Relationship](#delete-node-or-relationship)
- [Graph CRUD](#graph-crud)
  * [Create Graph](#create-graph)
  * [Get Graph](#get-graph)
- [Create and drop indexes](#create-and-drop-indexes)
  * [Create index](#create-index)
  * [Drop index](#drop-index)
- [Sessions and Transactions](#sessions-and-transactions)
  * [Execute multiple queries in a session](#execute-multiple-queries-in-a-session)
  * [Execute multiple queries in a transaction](#execute-multiple-queries-in-a-transaction)
    + [Simple transaction](#simple-transaction)
    + [Transaction with rollback](#transaction-with-rollback)

## Basic requirements

To make the client available and setup a connection run the following:

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687"))
~~~

Make the Neo4j client and connection available in the client and conn symbols respectively, this is a requirement for
the subsequent examples to work.

You might need to adjust the connection parameters to match your setup, see below.

## Connect to neo4j server

This section will show you how to connect with or without authentication and
how to change some of the basic options for the connection.

### Basic connection

~~~clojure
(def conn (client/connect "bolt://localhost:7687"))
~~~

### Authenticated connection

~~~clojure
(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))
~~~

### Connection with options

~~~clojure
(def conn (client/connect "bolt://localhost:7687" {:log {:level :info}}))
~~~

This also works on authenticated connections.

~~~clojure
(def conn
  (client/connect
    "bolt://localhost:7687"
    "neo4j"
    "password"
    {:log {:level :info}
           :encryption :none}))
~~~

In the current version we support the following options:

~~~
:log :level [:all :error :warn :info :off] - defaults to :warn
:encryption [:required :none] - defaults to :required"
~~~

## Disconnect from neo4j server

~~~clojure
(client/disconnect conn)
~~~

## Execute queries

This section shows how to execute raw bolt queries against on a open connection

### Execute query without parameters

~~~clojure
(client/execute! conn "MATCH (n:Person) RETURN n")
~~~

### Execute query with parameters

~~~clojure
(client/execute! conn "MATCH (n:Person {firstName: $first_name}) RETURN n" {:first_name "Neo"})
~~~

Notice that dashes is not allowed in parameter names

## Node CRUD

This section shows how to do basic CRUD operations on nodes through Neo4clj convenience functions.
To learn more about the Clojure representation of a node please see our [representations](representations.md) page

### Create a Node

~~~clojure
(client/create-node! conn {:ref-id "p"
                           :labels [:person]
                           :props {:first-name "Neo"
                                   :last-name "Anderson"}})
~~~

### Find nodes in Neo4j

The entry given to `find-nodes!` is a lookup representation. To learn more about the Clojure lookup representation see our [representations](representations.md) page

~~~clojure
(client/find-nodes! conn {:ref-id "p"
                          :labels [:person]
                          :props {:first-name "Neo"
                                  :last-name "Anderson"}})
~~~

### Update and Delete Node

See the section [Update and Delete Entity](#update-and-delete-neo4j-entity)


## Relationship CRUD

This section shows how to do basic CRUD operations on relationships through Neo4clj convenience functions.
To learn more about the Clojure representation of a relationship please see our [representations](representations.md) page

### Create a Relationship

The keys `from` and `to` are Lookup representations. To learn more about the Clojure representation of a lookup entry please see our [representations](representations.md) page

~~~clojure
(client/create-rel! conn {:ref-id "p"
                          :type :employee
                          :from {:labels [:person] :props {:first-name "Neo"}}
                          :to {:id 12}
                          :props {:position "Developer"}})
~~~

### Find relationships in Neo4j

The keys `from` and `to` are Lookup representations. To learn more about the Clojure representation of a lookup entry please see our [representations](representations.md) page

~~~clojure
(client/find-relationship! conn {:ref-id "p"
                                 :type :employee
                                 :from {:labels [:person] :props {:first-name "Neo"}}
                                 :to {:id 12}
                                 :props {:position "Developer"}})
~~~

Only `ref-id`, `to` and `from` are required keys in the relationship representation for finding relationships.

### Update and Delete Relationship

See the section [Update and Delete Entity](#update-and-delete-neo4j-entity)


## Update and Delete Neo4j Entity

Nodes and relationships are both part of the broader category named "entities" and this section will describe how to update and delete them.

In this section you can find examples on how to update specific parts a Node or Relationship and how to delete them.

### Update Node (Labels)

To change the labels of a node we have added two convenience functions.
Both the `add-labels!` and `remove-labels!` functions takes a lookup representation as second argument.
To learn more about the Clojure representation of a lookup entry please see our [representations](representations.md) page

#### Add labels

~~~clojure
(client/add-labels! conn {:id 45} [:person :salesman])
~~~

#### Remove labels

~~~clojure
(client/remove-labels! conn {:labels [:person] :props {:first-name "Neo"}} [:person :salesman])
~~~

### Update Node or Relationship (properties)

To change the properties of a node or relationship we have added two convenience functions.
Both the `update-props!` and `replace-props!` functions takes a lookup representation as second argument.

To learn more about the Clojure representation of a lookup entry please see our [representations](representations.md) page

#### Update properties

This function takes a connection, a lookup representation and the property map to update matched entities with.

It will update an existing properties map based on the following rules.

* Keys existing only in the given property map is added to the object
* Keys existing only in the property map on the found object is kept as is
* Keys existing in both property maps are updated with values from the given property map

~~~clojure
(client/update-props! conn {:labels [:person] :props {:first-name "Thomas" :first-name "Neo"}})
~~~

#### Replace properties

This function takes a connection, a lookup representation and the property map to replace the property map on matched entities with.

~~~clojure
(client/replace-props! conn {:labels [:person] :props {:first-name "Thomas" :last-name-only "Anderson"}})
~~~

So in the example we find all nodes with label `:person` and the property key value pair `:first-name "Thomas"` and replace the
whole property map, not only the key `:first-name` with the property-map `{:last-name-only "Anderson"}`

### Delete Node or Relationship

The `delete!` function takes a lookup representation as second argument and deletes all matches.

Notice: You need to ensure you have deleted all relatiohsips to a node before you can delete the node.

To learn more about the Clojure representation of a lookup entry please see our [representations](representations.md) page

~~~clojure
(client/delete! conn {:labels [:person] :props {:first-name "Neo"}})
~~~


## Graph CRUD

This section shows how to do basic CRUD operations on a whole graph through Neo4clj convenience functions.

### Create Graph

To make it easier to create nodes and relationships, we have made a function to do it in one single call.

The `lookups` key specifies a vector of [lookup representations](representations.md#lookup) referring existing nodes in the database.

The `nodes` key specifies a vector of [node representations](representations.md#node) to create.

The `rels` key specifies a vector of [relationship representations](representations.md#relationship) to create.
It is possible to use `ref-id` from the `lookups` vector in the `to` and `from` keys of the relationship, either as a map or directly as a string.

The `returns` key specifies a vector of `ref-id` from the other three keys to return as the result of the call. The values given can be a map or a string.

~~~clojure
(client/create-graph!
  conn
  {:lookups       [{:ref-id "c" :labels [:city] :props {:name "New York"}}]
   :nodes         [{:ref-id "p" :labels [:person] :props {:first-name "Neo"}}]
   :rels          [{:type :lives-in :from {:ref-id "p"} :to "c" :props {:born-here false}}]
   :returns       [{:ref-id "c"} "p"]})
~~~

### Get Graph

To make it easier to fetch nodes and relationships, we have made a function to do it all in one single call.

The `nodes` key specifies a vector of [node representations](representations.md#node) which is used in the relationships.
The nodes can also be specified directly in the reletaionship representation under the :rels key instead.
If a node with a given :ref-id is specified in the :nodes key, this representation is always used, even if a complete node
representation is given in the relationship.

The `rels` key specifies a vector of [relationship representations](representations.md#relationship) which needs to exists between the nodes to match.
There is some small differences compared to the normal representation. The main one being that the keys :from and :to are optional, and the additional
key :exists can be used to represent non-existent relationships.

The `returns` key specifies a vector of `ref-id` from the other two keys to return as the result of the call. The values given can be a map or a string.

~~~clojure
(client/get-graph
  conn
  {:nodes [{:ref-id "m" :labels [:company] :props {:name "The Matrix"} :id 19}]
   :rels    [{:ref-id "r1"
              :type :lives-in
              :from {:ref-id "p" :labels [:person] :props {:first-name "Neo"}}
              :to {:ref-id "c" :labels [:city]}}
             {:ref-id "r2"
              :type :works-for
              :from {:ref-id "p" :labels [:person] :props {:first-name "Neo"}}
              :to "m"
              :exists false}]
   :returns [{:ref-id "c"} "p" "r1"]})
~~~

## Create and drop indexes

To help handling index creation and drops, we have added convenience functions for theese operations

### Create index

Create an index on a single property

~~~clojure
(client/create-index! conn :person [:first-name])
~~~

Create an index across multiple properties

~~~clojure
(client/create-index! conn :person [:first-name :last-name])
~~~

### Drop index

Drop an index on a single property

~~~clojure
(client/drop-index! conn :person [:first-name])
~~~

Drop an index across multiple properties

~~~clojure
(client/drop-index! conn :person [:first-name :last-name])
~~~

## Sessions and Transactions

This section describes how to execute several queries in a session or transaction.

### Execute multiple queries in a session

By default all the operations you run in Neo4clj will be run in a separate session, but if you want to
run multiple queries in a session you can do it as shown below.

~~~clojure
(client/with-session conn session
  (client/create-node! session {:ref-id "p" :labels [:person] :props {:first-name "Neo"}})
  (client/create-node! session {:ref-id "p" :labels [:person] :props {:first-name "Morpheus"}}))
~~~

Notice the session variable is a placeholder, you can use as the connection in your queries.

### Execute multiple queries in a transaction

Running queries in a transaction ensures all queries are run without errors before commiting the changes to Neo4j.
In Neo4clj all transactions are auto-commiting on sucess.

#### Simple transaction

~~~clojure
(client/with-transaction conn transaction
  (client/create-node! transaction {:ref-id "p" :labels [:person] :props {:first-name "Neo"}})
  (client/create-node! transaction {:ref-id "p" :labels [:person] :props {:first-name "Morpheus"}}))
~~~

Notice the transaction variable is a placeholder, you can use as the connection in your queries.

#### Transaction with rollback

By default all exceptions occuring within the body of the `with-transaction` will result in a roll-back,
but it is also possible to do a manual rollback as shown below.

~~~clojure
(client/with-transaction conn transaction
  (client/create-node! transaction {:ref-id "p" :labels [:person] :props {:first-name "Neo"}})
  (client/create-node! transaction {:ref-id "p" :labels [:person] :props {:first-name "Morpheus"}})
  (client/rollback transaction))
~~~

Notice the transaction variable is a placeholder, you can use as the connection in your queries.