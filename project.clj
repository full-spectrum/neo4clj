(defproject com.github.full-spectrum/neo4clj "1.1.0"
  :description "Clojure client for Neo4j"
  :url "https://github.com/full-spectrum/neo4clj"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[com.github.full-spectrum/neo4clj-core "1.1.0"]
                 [com.github.full-spectrum/neo4clj-test "1.1.0"]]
  :sub ["neo4clj-core"
        "neo4clj-test"]
  :plugins [[lein-sub "0.3.0"]]
  :aliases {"test" ["sub" "test"]
            "test-all" ["sub" "test-all"]})
