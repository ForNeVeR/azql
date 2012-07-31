(ns azql.emit
  (:require [clojure.string :as s])
  (:use clojure.template))

(defrecord Sql [sql args])

(defprotocol SqlLike
  (^Sql as-sql [this] "Convert object to 'Sql'"))

(extend-type Sql
  SqlLike
  (as-sql [this] this))

(defn raw
  "Construct new raw SQL (without parameters)"
  ([s] (Sql. (str s) nil)))

(defn arg
  "Construct new parameter"
  [v]
  (Sql. "?" [v]))

(defn parse-qname
  "Split qualified name and return first part. Ex :a.val => [:a :val]"
  [qname]
  (if (vector? qname)
    (mapv keyword qname)
    (let [n (name qname)
          rp (s/split n #"\.")]
      (mapv keyword rp))))

(defn qualifier
  "Return first part of qname. Ex: :a.val => :a, :x => nil"
  [qname]
  (let [n (parse-qname qname)]
    (when (> (count n) 1)
      (first n))))

(defn ^:dynamic quote-name
  "Quote qname"
  [s]
  (str \" s \"))

(defn- emit-quoted-qname-part
  [p]
  (let [n (name p)]
    (if (= "*" n)
      n
      (quote-name n))))

(defn emit-qname
  "Parse qualified name and return SQL. Ex :a.val => \"a\".\"val\""
  [qname]
  (let [r (parse-qname qname)]
    (s/join \. (map emit-quoted-qname-part r))))

(defn ^Sql qname
  "Construct qualified name"
  ([qn] (Sql. (emit-qname qn) nil))
  ([qn alias] (Sql. (emit-qname [qn alias]) nil)))

(defn need-insert-space?
  [^String a ^String b]
  (not
   (or
    (nil? a)
    (.isEmpty a)
    (.startsWith b ")")
    (.endsWith a "(")
    (.startsWith b ","))))

(defn- join-sql-strings
  [strings]
  (let [sb (StringBuilder.)]
    (reduce (fn [prev curr]
              (when (and (not (nil? prev)) (need-insert-space? prev curr))
                (.append sb \space))
              (.append sb curr)
              curr) nil strings)
    (str sb)))

(extend-protocol SqlLike
  clojure.lang.Sequential
  (as-sql [this]
    (let [s (map as-sql (flatten this))]
       (Sql. (join-sql-strings (map #(.sql ^Sql %) s))
             (mapcat #(.args ^Sql %) s))))
  clojure.lang.Keyword
  (as-sql [this] (qname this))
  clojure.lang.Symbol
  (as-sql [this] (raw (name this)))
  Object
  (as-sql [this] (arg this)))

(defn ^Sql sql
  "Convert object to Sql"
  ([v]
     (let [v (as-sql v)]
       (assoc v :args (vec (.args v)) :sql (s/trim (.sql v)))))
  ([v & r] (sql (list* v r))))

;; todo: read http://savage.net.au/SQL/sql-92.bnf.html

(def <> not=)

(do-template
 [kname]
 (def kname (raw (str (s/replace (name 'kname) #"_" " "))))

 SELECT, FROM, WHERE, JOIN, IN, NOT_IN, ON,
 AND, OR, NOT, NULL, AS, IS_NULL, IS_NOT_NULL,
 ORDER_BY, GROUP_BY, HAVING_ON, DESC, ASC,
 LEFT_OUTER, RIGHT_OUTER, FULL_OUTER, CROSS, INNER)

(do-template
 [kname value]
 (def kname (raw value))

 NONE ""
 ASTERISK "*" 
 LEFT_PAREN "("
 RIGHT_PAREN ")"
 EQUALS "="
 NOT_EQUALS "<>"
 LESS "<"
 GREATER ">"
 LESS_EQUAL "<="
 GREATER_EQUAL ">="
 PLUS "+"
 MINUS "-"
 UMINUS "-"
 DIVIDE "/"
 MULTIPLY "*"
 COMMA ",")

(defn parenthesis
  "Surround into parenthesis"
  [e]
  [LEFT_PAREN e RIGHT_PAREN])
