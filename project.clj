(defproject fullspectrum/neo4clj "1.0.0-ALPHA7"
  :description "Clojure client for Neo4j"
  :url "https://github.com/full-spectrum/neo4clj"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[fullspectrum/neo4clj-core "1.0.0-ALPHA7"]
                 [fullspectrum/neo4clj-test "1.0.0-ALPHA7"]]
  :sub ["neo4clj-core"
        "neo4clj-test"]
  :plugins [[lein-sub "0.3.0"]]
  :aliases {"test" ["sub" "test"]
            "test-all" ["sub" "test-all"]})
