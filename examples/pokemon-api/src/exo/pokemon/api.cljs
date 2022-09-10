(ns exo.pokemon.api
  (:require
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.async.eql :as p.eql]
   [goog.object :as gobj]
   [lambdaisland.fetch :as fetch]
   [town.lilac.tree :as tree]))


(def pokemon-key-map
  {["is_default"] :pokemon/default?
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


(def pokemon-attrs
  (->> pokemon-key-map
       (filter #(= 1 (count (key %))))
       (map val)
       (vec)))
;; => (:pokemon/location-area-encounters
;;     :pokemon/stats
;;     :pokemon/abilities
;;     :pokemon/order
;;     :pokemon/base-experience
;;     :pokemon/id
;;     :pokemon/species
;;     :pokemon/default?
;;     :pokemon/forms
;;     :pokemon/weight
;;     :pokemon/moves
;;     :pokemon/types
;;     :pokemon/height
;;     :pokemon/name)


(pco/defresolver pokemon-by-id
  [{:keys [pokemon/id]}]
  {::pco/output pokemon-attrs}
  (-> (fetch/request
       (str "https://pokeapi.co/api/v2/pokemon/" id "/")
       :content-type :json
       :accept :json)
      (.then #(tree/transform-keys (:body %) pokemon-key-map))))


(pco/defresolver pokemon-by-name
  [{:keys [pokemon/name]}]
  {::pco/output pokemon-attrs}
  (-> (fetch/request
       (str "https://pokeapi.co/api/v2/pokemon/" name "/")
       :content-type :json
       :accept :json)
      (.then #(tree/transform-keys (:body %) pokemon-key-map))))


(pco/defresolver list-pokemon
  []
  {::pco/output [{:pokemon/list [:pokemon/name]}]}
  (-> (fetch/request
       "https://pokeapi.co/api/v2/pokemon/"
       :content-type :json
       :accept :json)
      (.then #(tree/transform-keys (gobj/get (:body %) "results") pokemon-key-map))
      (.then (fn [data]
               {:pokemon/list data}))))


(def env
  (pci/register [pokemon-by-id pokemon-by-name list-pokemon]))


(comment
  (-> (p.eql/process env '[{[:pokemon/id "35"] [:pokemon/name :pokemon/id]}])
      (.then prn))

  (-> (p.eql/process env '[{[:pokemon/name "venusaur"] [:pokemon/name :pokemon/id]}])
      (.then prn))

  (-> (p.eql/process env '[{:pokemon/list [:pokemon/name :pokemon/id :pokemon/weight]}])
      (.then prn)))
