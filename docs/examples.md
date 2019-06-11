# Examples

Below we have given examples of the most common operations in Neo4clj.

## Table of Contents

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


## Connect to neo4j server

This section will show you how to connect with or without authentication and
how to change some of the basic options for the connection.

### Basic connection

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687"))
~~~

### Authenticated connection

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))
~~~

### Connection with options

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" {:log {:level :info}}))
~~~

This also works on authenticated connections.

~~~clojure
(require '[neo4clj.client :as client])

(client/connect
  "bolt://localhost:7687"
  "neo4j"
  "password"
  {:log {:level :info}
         :encryption :none})
~~~

In the current version we support the following options:

~~~
:log :level [:all :error :warn :info :off] - defaults to :warn
:encryption [:required :none] - defaults to :required"
~~~

## Disconnect from neo4j server

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/disconnect conn)
~~~

## Execute queries

This section shows how to execute raw bolt queries against on a open connection

### Execute query without parameters

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687"))

(client/execute! conn "MATCH (n:Person) RETURN n")
~~~

### Execute query with parameters

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687"))

(client/execute! conn "MATCH (n:Person {firstName: $first_name}) RETURN n" {:first_name "Neo"})
~~~

Notice that dashes is not allowed in parameter names

## Node CRUD

This section shows how to do basic CRUD operations on nodes through Neo4clj convenience functions.
To learn more about the Clojure representation of a node please see our [representations](representations.md) page

### Create a Node

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/create-node! conn {:ref-id "p"
                           :labels [:person]
                           :props {:first-name "Neo"
                                   :last-name "Anderson"}})
~~~

### Find nodes in Neo4j

The entry given to `find-nodes!` is a lookup representation. To learn more about the Clojure lookup representation see our [representations](representations.md) page

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

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
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/create-relationship! conn {:ref-id "p"
                                   :type [:employee]
                                   :from {:labels [:person] :props {:first-name "Neo"}}
                                   :to {:id 12}
                                   :props {:position "Developer"}})
~~~

### Find relationships in Neo4j

To fetch relationships from the Neo4j database, please refer to the section [Get Graph](#get-graph)

### Update and Delete Relationship

See the section [Update and Delete Entity](#update-and-delete-neo4j-entity)


## Update and Delete Neo4j Entity

In this section you can find examples on how to update specific parts a Node or Relationship and how to delete them.

### Update Node (Labels)

To change the labels of a node we have added two convenience functions.
Both the `add-labels!` and `remove-labels!` functions takes a lookup representation as second argument.
To learn more about the Clojure representation of a lookup entry please see our [representations](representations.md) page

#### Add labels

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/add-labels! conn {:id 45} [:person :salesman])
~~~

#### Remove labels

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/remove-labels! conn {:labels [:person] :props {:first-name "Neo"}} [:person :salesman])
~~~

### Update Node or Relationship (properties)

To change the properties of a node or relationship we have added two convenience functions.
Both the `update-properties!` and `replace-properties!` functions takes a lookup representation as second argument.
To learn more about the Clojure representation of a lookup entry please see our [representations](representations.md) page

#### Update properties

This function will update an existing properties map based on the following rules.

⋅⋅* Keys existing only in the given property map is added to the object
..* Keys existing only in the property map on the found object is kept as is
..* Keys existing in both property maps are updated with values from the given property map

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/update-properties! conn {:labels [:person] :first-name "Thomas"} {:first-name "Neo"})
~~~

#### Replace properties

This function will replace an existing property map with the given one

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/update-properties! conn {:labels [:person] :first-name "Thomas"} {:last-name-only "Anderson"})
~~~

### Delete Node or Relationship

The `delete!` function takes a lookup representation as second argument and deletes all matches.

Notice: You need to ensure you have deleted all relatiohsips to a node before you can delete the node.

To learn more about the Clojure representation of a lookup entry please see our [representations](representations.md) page

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/delete! conn {:labels [:person] :first-name "Neo"})
~~~


## Graph CRUD

This section shows how to do basic CRUD operations on a whole graph through Neo4clj convenience functions.

### Create Graph

To make it easier to create nodes and relationships, we have made a function to do it in one single call.

The `lookups` key allows you to lookup and refer existing nodes in the Neo4j database. The entries are lookup representations.
The `nodes` key dictates what to nodes create. The entries are node representations.
The `relatioships` key dictates what to create. The entries are relationship representations and it's possible to use lookups ref-id in the `to` and `from` keys.
The `returns` key dictates which entities to return as the result of the call. This is a vector of ref-id from collections in the other three keys.

To learn more about the Clojure representation of a create graph structure and it's individual parts please see our [representations](representations.md) page

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/create-graph!
  conn
  {:lookups       [{:ref-id "c" :labels [:city] :props {:name "New York"}}]
   :nodes         [{:ref-id "p" :labels [:person] :props {:first-name "Neo"}}]
   :relationships [{:type :lives-in :from {:ref-id "p"} :to {:ref-id "c"} :props {:born-here false}}]
   :returns       ["c" "p"]})
~~~

### Get Graph

To make it easier to fetch nodes and relationships, we have made a function to do it all in one single call.

The `nodes` key dictates which nodes to get. The entries are lookup representations.
The `relatioships` key dictates which relationships needs to exists between the nodes. The entries are relationship representations.
The `returns` key dictates which entities to return as the result of the call. This is a vector of ref-id from the nodes and relationship keys.

To learn more about the Clojure representation of a get graph structure and it's individual parts please see our [representations](representations.md) page

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/get-graph!
  conn
  {:nodes         [{:ref-id "c" :labels [:city]}
                   {:ref-id "p" :labels [:person] :props {:first-name "Neo"}}]
   :relationships [{:ref-id "r" :type :lives-in :from {:ref-id "p"} :to {:ref-id "c"}}]
   :returns       ["c" "p" "r"]})
~~~


## Create and drop indexes

To help handling index creation and drops, we have added convenience functions for theese operations

### Create index

Create an index on a single property

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/create-index! conn :person [:first-name])
~~~

Create an index across multiple properties

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/create-index! conn :person [:first-name :last-name])
~~~

### Drop index

Drop an index on a single property

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/drop-index! conn :person [:first-name])
~~~

Drop an index across multiple properties

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/drop-index! conn :person [:first-name :last-name])
~~~

## Sessions and Transactions

This section describes how to execute several queries in a session or transaction.

### Execute multiple queries in a session

By default all the operations you run in Neo4clj will be run in a separate session, but if you want to
run multiple queries in a session you can do it as shown below.

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/with-session conn session
  (cliet/create-node! session {:ref-id "p" :labels [:person] :props {:first-name "Neo"}})
  (cliet/create-node! session {:ref-id "p" :labels [:person] :props {:first-name "Morpheus"}}))
~~~

Notice the session variable is a placeholder, you can use as the connection in your queries.

### Execute multiple queries in a transaction

Running queries in a transaction ensures all queries are run without errors before commiting the changes to Neo4j.
In Neo4clj all transactions are auto-commiting on sucess.

#### Simple transaction

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/with-transaction conn transaction
  (cliet/create-node! transaction {:ref-id "p" :labels [:person] :props {:first-name "Neo"}})
  (cliet/create-node! transaction {:ref-id "p" :labels [:person] :props {:first-name "Morpheus"}}))
~~~

Notice the transaction variable is a placeholder, you can use as the connection in your queries.

#### Transaction with rollback

By default all exceptions occuring within the body of the `with-transaction` will result in a roll-back,
but it is also possible to do a manual rollback as shown below.

~~~clojure
(require '[neo4clj.client :as client])

(def conn (client/connect "bolt://localhost:7687" "neo4j" "password"))

(client/with-transaction conn transaction
  (cliet/create-node! transaction {:ref-id "p" :labels [:person] :props {:first-name "Neo"}})
  (cliet/create-node! transaction {:ref-id "p" :labels [:person] :props {:first-name "Morpheus"}})
  (client/rollback trasaction))
~~~

Notice the transaction variable is a placeholder, you can use as the connection in your queries.