(ns neo4clj.test-utils
  "Neo4j 4.2.5 does not work with JDK version 16 - ServiceBuilder cannot \"build\" and throws exception."
  (:require [neo4clj.client :as neo4j])
  (:import [org.neo4j.configuration.connectors BoltConnector]
           [org.neo4j.dbms.api DatabaseManagementServiceBuilder]
           [org.neo4j.io.fs FileUtils]
           [java.nio.file Path Files]))

(defmacro with-db
  [[conn {:keys [file-uri initial-data host port]}] & body]
  `(let [path# (if ~file-uri
                 (Path/of (java.net.URI. ~file-uri))
                 (Files/createTempDirectory nil (into-array java.nio.file.attribute.FileAttribute [])))
         host# (or ~host "localhost")
         port# (or ~port 17687)
         service# (-> (DatabaseManagementServiceBuilder. path#)
                      (.setConfig BoltConnector/enabled true)
                      (.setConfig BoltConnector/listen_address (org.neo4j.configuration.helpers.SocketAddress. host# port#))
                      (.build))]
     (try
       (let [tx# (-> service#
                     (.database  org.neo4j.configuration.GraphDatabaseSettings/DEFAULT_DATABASE_NAME)
                     (.beginTx))]
         (doseq [cypher# ~initial-data]
           (.execute tx# cypher#))
         (.commit tx#)
         (let [~conn (neo4j/connect (str "bolt://" host# ":" port#))]
           ~@body))
       (finally
         (.shutdown service#)
         (FileUtils/deleteDirectory path#)))))
