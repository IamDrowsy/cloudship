(ns cloudship.util.query
  (:require [instaparse.core :as insta]))

(def soql-grammar
  "<query> = <'SELECT'> fieldList <'FROM'> object options?
   fieldList = fieldListEntry [<','> fieldListEntry]*
   fieldListEntry = (field|subquery|typeof)
   <typeof> = 'TYPEOF' <any* 'END'>
   subquery = <'('> query <')'>
   <field> = fieldname|aggregate
   <aggregate> = #'[a-zA-Z0-9]+' '(' fieldname? ')'
   object = objectname
   <options> = ['WHERE'|'WITH'|'GROUP BY'|'ORDER BY'|'LIMIT'|'OFFSET'|'FOR'] any*
   <any> = <#'.'>
   <objectname> = #'[a-zA-Z0-9_]+'
   <fieldname> = #'[a-zA-Z0-9_.]+'")

(def whitespace-parser
  (insta/parser
    "whitespace = #'\\s+'"))

(def soql-parser
  (insta/parser soql-grammar
                :auto-whitespace whitespace-parser
                :string-ci true))

(defn object-from-query [query-string]
  (second (last (soql-parser query-string))))