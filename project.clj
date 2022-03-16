(defproject fullspectrum/neo4clj "1.0.0-ALPHA6"
  :description "Clojure client for Neo4j"
  :url "https://github.com/full-spectrum/neo4clj"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[camel-snake-kebab "0.4.2"]
                 [clojure.java-time "0.3.2"]
                 [org.clojure/clojure "1.10.3"]
                 [org.neo4j.driver/neo4j-java-driver "4.4.3"]]
  :pedantic? false
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "1.1.0"]
                                  [criterium "0.4.6"]]
                   :source-paths ["dev"]}
             :test {:dependencies [[org.neo4j/neo4j-dbms "4.4.3"]
                                   [org.neo4j/neo4j "4.4.3"]
                                   [org.neo4j/neo4j-graphdb-api "4.4.3"]
                                   [org.neo4j/neo4j-bolt "4.4.3"]]}})
