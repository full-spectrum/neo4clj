(ns neo4clj.test-utils
  "Neo4j 4.2.5 does not work with JDK version 16 - ServiceBuilder cannot \"build\" and throws exception."
  (:require [neo4clj.client :as neo4j])
  (:import [org.neo4j.configuration.connectors BoltConnector]
           [org.neo4j.dbms.api DatabaseManagementServiceBuilder]
           [org.neo4j.io.fs FileUtils]
           [java.nio.file Path Files]))

(defmacro with-db
  [conn {:keys [file-uri initial-data]} & body]
  `(let [path# (if ~file-uri
                 (Path/of (java.net.URI. ~file-uri))
                 (Files/createTempDirectory nil (into-array java.nio.file.attribute.FileAttribute [])))
         service# (-> (DatabaseManagementServiceBuilder. path#)
                      (.setConfig BoltConnector/enabled true)
                      (.build))]
     (try
       (let [tx# (-> service#
                     (.database  org.neo4j.configuration.GraphDatabaseSettings/DEFAULT_DATABASE_NAME)
                     (.beginTx))]
         (doseq [cypher# ~initial-data]
           (.execute tx# cypher#))
         (.commit tx#)
         (let [~conn (neo4j/connect "bolt://localhost:7687")]
           ~@body))
       (finally
         (.shutdown service#)
         (FileUtils/deleteDirectory path#)))))
