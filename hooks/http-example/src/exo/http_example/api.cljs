(ns exo.http-example.api)


(def pokemon-key-map
  {["is_default"] :pokemon.default?
   ["species"] :pokemon/species
   ["species" "name"] :pokemon.species/name
   ["species" "url"] :pokemon.species/url
   ["height"] :pokemon/height
   ["forms"] :pokemon/forms
   ["forms" "name"] :pokemon.form/name
   ["forms" "url"] :pokemon.form/url
   ["moves"] :pokemon/moves
   ["moves" "move"] :pokemon.move/info
   ["moves" "move" "name"] :pokemon.move/name
   ["moves" "move" "url"] :pokemon.move/url
   ;; eliding version_group_details
   ["id"] :pokemon/id
   ["types"] :pokemon/types
   ["types" "slot"] :pokemon.type/slot
   ["types" "type"] :pokmeon.type/info
   ["types" "type" "name"] :pokemon.type/name
   ["types" "type" "url"] :pokemon.type/url
   ["name"] :pokemon/name
   ["abilities"] :pokemon/abilities
   ["abilities" "ability"] :pokemon.ability/info
   ["abilities" "ability" "name"] :pokemon.ability/name
   ["abilities" "ability" "url"] :pokemon.ability/url
   ["abilities" "is_hidden"] :pokemon.ability/hidden?
   ["abilities" "slot"] :pokemon.ability/slot
   ;; eliding past_types
   ;; eliding game_indices
   ["location_area_encounters"] :pokemon/location-area-encounters
   ["order"] :pokemon/order
   ;; eliding sprites
   ["base_experience"] :pokemon/base-experience
   ;; eliding held_items
   ["stats"] :pokemon/stats
   ["stats" "base_stat"] :pokemon.stat/base-stat
   ["stats" "effort"] :pokemon.stat/effort
   ["stats" "stat"] :pokemon.stat/info
   ["stats" "stat" "name"] :pokemon.stat/name
   ["stats" "stat" "url"] :pokemon.stat/url
   ["weight"] :pokemon/weight})
