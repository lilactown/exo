(ns demo.pokemon.api
  (:require
   [clojure.string :as string]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.async.eql :as p.eql]
   [demo.pokemon.util :as util]
   [goog.object :as gobj]
   [lambdaisland.fetch :as fetch]))


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
      (.then #(util/transform-keys (:body %) pokemon-key-map))))


(pco/defresolver pokemon-by-name
  [{:keys [pokemon/name]}]
  {::pco/output pokemon-attrs}
  (-> (fetch/request
       (str "https://pokeapi.co/api/v2/pokemon/" name "/")
       :content-type :json
       :accept :json)
      (.then #(util/transform-keys (:body %) pokemon-key-map))))


(pco/defresolver pokemon-evolution-id
  [{:keys [pokemon.evolution/url]}]
  {::pco/output [:pokemon.evolution/id]}
  {:pokemon.evolution/id (last (string/split url "/"))})


(def species-key-map
  {["id"] :pokemon.species/id
   ["name"] :pokemon.species/name
   ["order"] :pokemon.species/order
   ["gender_rate"] :pokemon.species/gender-rate
   ["capture_rate"] :pokemon.species/capture-rate
   ["base_happiness"] :pokemon.species/base-happiness
   ["is_baby"] :pokemon.species/baby?
   ["is_legendary"] :pokemon.species/legendary?
   ["is_mythical"] :pokemon.species/mythical?
   ["evolves_from_species"] :pokemon.species/evolves-from
   ["evolves_from_species" "name"] :pokemon.species/name
   ["evolves_from_species" "url"] :pokemon.species/url
   ["evolution_chain"] :pokemon.species/evolution-chain
   ["evolution_chain" "url"] :pokemon.evolution/url})


(def species-attrs
  (->> species-key-map
       (filter #(= 1 (count (key %))))
       (map val)
       (vec)))


(pco/defresolver species-by-name
  [{:keys [pokemon.species/name]}]
  {::pco/output species-attrs}
  (-> (fetch/request
       (str "https://pokeapi.co/api/v2/pokemon-species/" name "/")
       :content-type :json
       :accept :json)
      (.then #(util/transform-keys (:body %) species-key-map))))


(defn transform-evolution-chain-key
  [path x]
  (cond
    (= "evolves_to" (last path)) :pokemon.evolution/evolves-to
    (= "species" (last path)):pokemon.evolution/species
    (= ["species" "name"] (take-last 2 path)) :pokemon.species/name
    (= ["species" "url"] (take-last 2 path)) :pokemon.species/url))


(pco/defresolver evolution-by-id
  [{:keys [pokemon.evolution/id]}]
  {::pco/output [:pokemon.evolution/id
                 {:pokemon.evolution/chain [:pokemon.evolution/evolves-to
                                            :pokemon.evolution/species]}]}
  (-> (fetch/request
       (str "https://pokeapi.co/api/v2/evolution-chain/" id "/")
       :content-type :json
       :accept :json)
      (.then (fn [data]
               {:pokemon.evolution/id (gobj/get (:body data) "id")
                :pokemon.evolution/chain
                (util/transform-keys
                 (gobj/get (:body data) "chain")
                 transform-evolution-chain-key)}))))


(defn- parse-page-from-url
  [url]
  (re-seq #"[\?|&](.+)=(.+)[&|$]" url))

(comment
  (parse-page-from-url "https://pokeapi.co/api/v2/pokemon/?offset=20&limit=20"))

(pco/defresolver list-pokemon
  [{:keys [page]}]
  {::pco/input [(pco/? :page)]
   ::pco/output [{:pokemon/list [:pokemon/name]}
                 :page :next]}
  (-> (fetch/request
       "https://pokeapi.co/api/v2/pokemon/"
       :content-type :json
       :accept :json)
      (.then #(util/transform-keys (gobj/get (:body %) "results") pokemon-key-map))
      (.then (fn [data]
               {:pokemon/list data}))))


(def env
  (pci/register [pokemon-by-id
                 pokemon-by-name
                 pokemon-evolution-id
                 species-by-name
                 evolution-by-id
                 list-pokemon]))


(comment
  (-> (p.eql/process env '[{[:pokemon/id "35"] [:pokemon/name :pokemon/id]}])
      (.then prn))

  (-> (p.eql/process
       env
       '[{[:pokemon/name "venusaur"]
          [:pokemon/name :pokemon/id
           {:pokemon/species
            [:pokemon.species/name
             :pokemon.species/mythical?
             {:pokemon.species/evolution-chain
              [:pokemon.evolution/id
               {:pokemon.evolution/chain
                [{:pokemon.evolution/species
                  [:pokemon.species/name :pokemon.species/id]}
                 {:pokemon.evolution/evolves-to
                  [{:pokemon.evolution/species
                    [:pokemon.species/name :pokemon.species/id]}
                   {:pokemon.evolution/evolves-to ...}]}]}]}]}]}])
      (.then prn))

  ;; slowww
  (-> (p.eql/process
       env
       '[{:pokemon/list [:pokemon/name :pokemon/id :pokemon/weight]}])
      (.then prn)))
