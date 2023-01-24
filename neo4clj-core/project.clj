(defproject com.github.full-spectrum/neo4clj-core "1.1.0"
  :description "Neo4j core library and client"
  :url "https://github.com/full-spectrum/neo4clj"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[camel-snake-kebab "0.4.3"]
                 [clojure.java-time "1.1.0"]
                 [org.clojure/clojure "1.11.1"]
                 [org.neo4j.driver/neo4j-java-driver "5.3.0"]]
  :pedantic? false
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "1.1.0"]
                                  [criterium "0.4.6"]]
                   :source-paths ["dev"]}
             :test {:dependencies [[com.github.full-spectrum/neo4clj-test "1.1.0"]]}})
