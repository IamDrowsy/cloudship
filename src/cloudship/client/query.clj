(ns cloudship.client.query
  (:require [instaparse.core :as insta]))

(def SOQL-EBNF
  "<S> = <'SELECT'> fields <'FROM'> object scope? where? with? group? order? limit? offset? for?
   fields = field (<','> field)?
   <field> = name|subquery
   subquery = '(' #'[^)]' ')'
   object = name
   scope = <'USING'> <'SCOPE'> name
   where = <'WHERE'> condition
   condition = todo
   with = <'WITH'> todo
   group = <'GROUP'> <'BY'> todo
   order = <'ORDER'> <'BY'> todo
   limit = <'LIMIT'> #'[0-9]+'
   offset = <'OFFSET'> #'[0-9]+'
   for = <'FOR'> todo
   todo = name
   <name> = #'[a-zA-Z0-9_]+'")

(def soql-parser
  (insta/parser SOQL-EBNF
                :auto-whitespace :standard
                :string-ci true))

(defn parse-query [query-string]
  (into {} (insta/transform {:fields (fn [& entries] [:fields entries])}
                            (soql-parser query-string))))