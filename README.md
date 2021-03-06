# AZQL

AZQL is a SQL like DSL for Clojure.

Main ideas of this project:

- no schema restrictions, ORM-mappings etc;
- DSL should be closer to native SQL as much as possible;
- no fake optimizations (modern DBs are clever enough);
- protection from injections, but not from invalid queries;
- sometimes we want to include vendor-specific parts into queries.


## Installation

Add the following to your project.clj:

```clj
[azql "0.2.0"]
```


### Basic usage

AZQL syntax is quite similar to SQL:

```clj

(def db #<jdbc connection params>)

(fetch-all db
  (select
    (fields [:id :name :email])
    (from :a :Users)
    (where (= :a.role "ADMIN"))))
```

After macroexpansions:

```clj
(fetch-all db
  (->
    (select*)
    (fields* {:id :id, :name :name, :email :email})
    (join* nil :a :Users) ; nil means 'implicit cross join'
    (where* (list '= :a.role "ADMIN")))

```

Function `fetch-all` executes query and converts `resultset-seq` into vector.
Library provides some alternatives:

- `fetch-one` fetches and returns only one record or raises an exception if more 
  than one record may be returned;
- `fetch-single` fetches and returns only single value (one row and one column);
- `with-fetch` executes arbitrary code with opened `resultset-seq`;

Example:

```clj
(with-fetch db [f (table :Users)]
  (reduce + (map :rating f)))
```

It is possible to compose additional conditions:

```clj
(def all-users (table :Users))
(def banned-users
  (-> all-users (where (= :status "BANNED"))))
(def banned-admins
  (-> banned-users (where (= :role "ADMIN")))
(println (fetch-all db banned-admins))
```

Also you can use map-style conditions:

```clj
(select
  (from :Users)
  (where {:first "Ivan", :last "Ivanov"}))
```

The resulting SQL string is available through `azql.emit/as-sql` function:

```clj
user> (azql.emit/as-sql (select (from :Users) (where {:id 123})))
#<"SELECT * FROM \"Users\" WHERE (\"id\" = ?)" 123>
```

### Joins

AZQL supports all types of JOINs:

```clj
(select
  (from :a :A)                      ; table 'A', alias 'a', implicit cross join
  (join :b :B (= :a.x :b.y))        ; inner join
  (join-cross :c :B)                ; explicit cross join
  (join-inner :D {:a.x :D.y})
  (join-full :e :E {:e.x :a.x, :e.y :D.y)))
```

The only restriction is that first `join` must be *implicit cross join*
(function `from`). Vendor-specific JOINs are supported too:

```clj
(select
  (from :a :TableOne)
  (join* (raw "MEGA JOIN") :b :TableTwo (= :b.x = :a.y)))
```

### Ordering

You can use ordering:

```clj
(select
  (from :A)
  (order :field1)
  (order :field2 :desc)
  (order (+ :x :y) :asc))
```

### Grouping

AZQL supports grouping:

```clj
(select
  (fields {:name :u.name})
  (from :u :Users)
  (join :p :Posts (= :p.userid :u.id))
  (group :u.name)
  (having (> 10 (count :p.id))))
```

### Subqueries

Subqureies are supported too:

```clj
(def all-users (select (from :Users)))

(def all-active-users
  (select
  (from all-users)
  (where (= :status "ACTIVE"))))

(fetch-all db all-active-users)
```

You may also use `ALL`, `ANY` and `SOME` comparison conditions:

```clj
(select
  (from :u :Users)
  (where {:u.id (any :id (table :ActiveUsers))}))
```

Note that AZQL treats all forms in `where` macro as SQL-functions, except of
`select` and `table`. You must use `select` in your subqueries or pass them
as value:

```clj
(let [sq (fields (table :ActiveUsers) [:id])]
  (select
    (from :u :Users)
    (where (= :u.id (any sq)))))
```

### Limit and offset

Limiting and offset are well supported:

```clj
(select
  (from :u :Users)
  (where (like? :name "%Andrei%"))
  (limit 100)
  (offset 25))
```

### Composed queries

Unions:

```clj
(compose :union
  (modifier :all)
  (select
    [:name]
    (from :Users))
  (select
    [:name]
    (from :Accounts)
    (where {:status 1}))
  (order :name))
```

AZQL provides shortcuts `union`, `intersect` and `except`:

```clj
(union
  (intersect (table :A) (table :B))
  (except (table :C) (table :D)))
```

### CRUD

All CRUD operations are supported. You had already learn about Read, here are 
the missing bits.

Insert new records:

```clj
(insert! db :table
  (values [{:field1 1, :field2 "value"}
           {:field1 2, :field2 "value"}]))
```

Update:

```clj
(update! db :table
  (setf :cnt (+ :cnt 1))
  (setf :vale "new-value")
  (where (in? :id [1 2 3])))
```

Delete:

```clj
(delete! db :table
  (where (= :id 1)))
```

### License

Copyright © 2012 Andrei Zhlobich

Distributed under the Eclipse Public License, the same as Clojure.
