(ns neo4clj.internal.sanitize
  (:require [camel-snake-kebab.core :refer [->camelCaseString
                                            ->PascalCaseString
                                            ->SCREAMING_SNAKE_CASE_STRING
                                            ->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]))

(defn cypher-label [label]
  "Sanitize a node label based on the Cypher style guide"
  (->PascalCaseString label))

(defn cypher-labels [labels]
  "Sanitize node labels based on the Cypher style guide"
  (map cypher-label labels))

(defn cypher-property-key [key]
  "Sanitize a property key based on the Cypher style guide"
  (->camelCaseString key))

(defn cypher-parameter-keys [params]
  "Sanitize a parameter map based on the Neo4J driver requirements"
  (transform-keys ->camelCaseString params))

(defn cypher-relation-type [type]
  "Sanitize a relationship type based on the Cypher style guide"
  (when type
    (->SCREAMING_SNAKE_CASE_STRING type)))

(defn clj-label [label]
  "Sanitize a node label based on the Clojure style guide"
  (->kebab-case-keyword label))

(defn clj-labels [labels]
  "Sanitize node labels based on the Clojure style guide"
  (map clj-label labels))

(defn clj-properties [props]
  (transform-keys ->kebab-case-keyword props))

(defn clj-relation-type [type]
  "Sanitize a relationship type based on the Clojure style guide"
  (->kebab-case-keyword type))
