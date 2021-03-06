(ns azql.connection-test
  (:use azql.connection)
  (:use clojure.test)
  (:require [clojure.java.jdbc :as jdbc])
  (:use azql.test-database))

(comment use-fixtures :each #(try (%) (finally (close-global-connection))))

(comment deftest test-global-connection
  (open-global-connection database-connection)
  (is (not (jdbc/find-connection)))
  (is (with-global-connection (jdbc/find-connection)))
  (close-global-connection)
  (with-global-connection nil))

(comment deftest test-ignore-scoped-connection
  (open-global-connection database-connection)
  (is
    (not
      (identical?
        (with-global-connection (jdbc/find-connection))
        (jdbc/with-connection database-connection (jdbc/find-connection))))))
