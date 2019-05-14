(defproject neo4clj "1.0.0-SNAPSHOT"
  :description "Clojure client for Neo4j"
  :dependencies [[camel-snake-kebab "0.4.0"]
                 [clojure.java-time "0.3.2"]
                 [com.rpl/specter "1.1.2"]
                 [org.clojure/clojure "1.10.0"]
                 [org.neo4j.driver/neo4j-java-driver "1.7.2"]]
  :main full-spectrum.neo4clj.client
  :aot [full-spectrum.neo4clj.client]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [criterium "0.4.4"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns user}}})
