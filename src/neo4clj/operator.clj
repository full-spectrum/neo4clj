(ns neo4clj.operator
  (:require [clojure.string :as str]))

(defn exists [entity]
  (assoc entity :operator :exists))

(defn not-exists [entity]
  (assoc entity :operator :not-exists))
