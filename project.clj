(defproject fullspectrum/neo4clj "1.0.0-ALPHA6"
  :description "Clojure client for Neo4j"
  :url "https://github.com/full-spectrum/neo4clj"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[camel-snake-kebab "0.4.2"]
                 [clojure.java-time "0.3.2"]
                 [com.rpl/specter "1.1.3"]
                 [org.clojure/clojure "1.10.3"]
                 [org.neo4j.driver/neo4j-java-driver "4.2.5"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "1.1.0"]
                                  [criterium "0.4.6"]]
                   :source-paths ["dev"]}})
