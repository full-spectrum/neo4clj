(ns neo4clj.cypher
  (:require [clojure.string :as str]
            [neo4clj.sanitize :as sanitize]))

(defn gen-ref-id
  "Generate a unique id that can be used to reference an Neo4j entity
  until they are assigned one by the database, which happens when
  they are stored."
  []
  (str (gensym)))

(defn labels
  "Takes a collection of labels (keywords) and returns a Cypher string
  representing the labels. Order is reversed but doesn't matter."
  [labels]
  (->>
   labels
   (reduce #(conj %1 (sanitize/cypher-label %2) ":") '())
   str/join))
