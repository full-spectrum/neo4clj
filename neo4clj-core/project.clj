(defproject full-spectrum/neo4clj-core "1.0.0-ALPHA7"
  :description "Neo4j core library and client"
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
             :test {:dependencies [[full-spectrum/neo4clj-test "1.0.0-ALPHA7"]]}})
