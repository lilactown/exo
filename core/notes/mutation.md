# Mutation

## API sketch

```clojure
(defn update-pokemon
  [id data]
  (exo/mutation
   `[(pokemon/update-by-id ~id ~data)]))

(defn delete-pokemon
  [id]
  (exo/mutation
   `(pokemon/delete-by-id ~id)))

(defnc my-component
  []
  (let [[delete-pokemon! deleting?] (exo/use-mutation (delete-pokemon 1))]
    (d/div
      (b/button {:on-click #(delete-pokemon!)}))))
```
