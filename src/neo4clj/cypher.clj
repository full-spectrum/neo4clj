(ns neo4clj.cypher
  (:require [clojure.string :as str]
            [neo4clj.sanitize :as sanitize]))

(defn labels
  "Takes a collection of labels (keywords) and returns a Cypher string
  representing the labels. Order is reversed but doesn't matter."
  [labels]
  (->>
   labels
   (reduce #(conj %1 (sanitize/cypher-label %2) ":") '())
   str/join))
