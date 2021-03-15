(defproject fullspectrum/neo4clj "1.0.0-ALPHA4"
  :description "Clojure client for Neo4j"
  :url "https://github.com/full-spectrum/neo4clj"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[camel-snake-kebab "0.4.0"]
                 [clojure.java-time "0.3.2"]
                 [com.rpl/specter "1.1.2"]
                 [org.clojure/clojure "1.10.0"]
                 [org.neo4j.driver/neo4j-java-driver "4.2.0"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [criterium "0.4.4"]]
                   :source-paths ["dev"]}})
