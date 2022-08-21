# Data masking

## Goal

Data masking is a way of making it so that components don't have implicit
dependencies on data fetched by parent or sibling components. It enforces being
explicit about data requirements in components, which makes it easier to move
and delete components without fear of breaking other parts of the app.

## API Sketch

```clojure
(def pokemon-info-fragment
  (exo/fragment [:pokemon/id :pokemon/name :pokemon/weight]))

(defn pokemon-query
  [id]
  `[{(:pokemon {:pokemon/id ~id})
     ~pokemon-info-fragment}])

(defnc pokemon-info
  [{:keys [pokemon-data]}]
  (let [{:pokemon/keys [id name weight]} (exo/use-fragment
                                          pokemon-info-fragment
                                          pokemon-data)]
    (d/div
     (d/div "ID: " id)
     (d/div "Name: " name)
     (d/div "Weight: " weight))))

(defnc app
  []
  (let [{:keys [data loading?]} (exo/use-query (pokemon-query 0))]
    (d/div
     (d/div "loading? " loading?)
     (when data
       ($ pokemon-info {:pokemon-data (:pokemon data)})))))
```

## Design

Fragments are contained in queries

See https://relay.dev/docs/guided-tour/rendering/fragments/#composing-fragments

You cannot access fields in a fragment unless you explicitly depend on the fragment

use-query needs to return a map-like that has metadata on what fragments are contained in it

use-fragment looks up the fragment in the metadata and gets the result. need a way of associating fragment with the slice of the query result.

use-fragment returns a map-like that has metadata on what fragments are contained in it

## Potential problems

Moving part of a query into a fragment is a breaking change... how to detect
breakage without static typing?
