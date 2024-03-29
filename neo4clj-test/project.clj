(defproject com.github.full-spectrum/neo4clj-test "1.1.0"
  :description "Neo4j test library"
  :url "https://github.com/full-spectrum/neo4clj"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.github.full-spectrum/neo4clj-core "1.1.0"]
                 [org.neo4j.driver/neo4j-java-driver "5.3.0"]
                 [org.neo4j/neo4j-dbms "5.3.0"]
                 [org.neo4j/neo4j "5.3.0" :exclusions [org.slf4j/slf4j-api]]
                 [org.neo4j/neo4j-graphdb-api "5.3.0"]
                 [org.neo4j/neo4j-bolt "5.3.0" :exclusions [org.slf4j/slf4j-api]]]
  :pedantic? :warn)
