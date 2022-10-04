(defproject full-spectrum/neo4clj-test "1.0.0"
  :description "Neo4j test library"
  :url "https://github.com/full-spectrum/neo4clj"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [full-spectrum/neo4clj-core "1.0.0"]
                 [org.neo4j.driver/neo4j-java-driver "4.4.9"]
                 [org.neo4j/neo4j-dbms "4.4.11"]
                 [org.neo4j/neo4j "4.4.11" :exclusions [org.slf4j/slf4j-api]]
                 [org.neo4j/neo4j-graphdb-api "4.4.11"]
                 [org.neo4j/neo4j-bolt "4.4.11" :exclusions [org.slf4j/slf4j-api]]]
  :pedantic? :warn)
