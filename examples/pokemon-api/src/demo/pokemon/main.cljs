(ns demo.pokemon.main
  (:require
   [cljs.pprint :refer [pprint]]
   [demo.pokemon.api :as api]
   [exo.core :as exo]
   [exo.hooks]
   [helix.core :refer [defnc $]]
   [helix.dom :as d]
   [helix.hooks :as hooks]
   ["react-dom/client" :as rdom]))

(defonce exo-config
  (exo/create-config
   {:network (fn [query _opts]
               (api/fetch-query query))}))


(defn pokemon-query
  [id]
  [{[:pokemon/id id] [:pokemon/name
                      :pokemon/id
                      :pokemon/height
                      :pokemon/weight
                      {:pokemon/sprites [:pokemon.sprites/front-default]}]}])


(defn evolution-chain-query
  [id]
  [{[:pokemon/id id] #_[:pokemon/id :pokemon/species]
    [:pokemon/id
     {:pokemon/species
      [:pokemon/name
       {:pokemon.species/evolution-chain
        [:pokemon.evolution/id
         {:pokemon.evolution/chain
          [{:pokemon.evolution/species
            [:pokemon/name
             :pokemon/id
             {:pokemon/sprites [:pokemon.sprites/front-default]}]}
           {:pokemon.evolution/evolves-to
            [{:pokemon.evolution/species
              [:pokemon/name
               :pokemon/id
               {:pokemon/sprites [:pokemon.sprites/front-default]}]}
             {:pokemon.evolution/evolves-to '...}]}]}]}]}]}])


(defnc evolution
  [{:keys [species evolves-to]}]
  (d/div
   {:style {:padding-left 15
            :border-left "1px dotted gray"}}
   (d/img {:src (get-in species [:pokemon/sprites :pokemon.sprites/front-default])})
   (d/pre
    {:style {:display "inline-block"}}
    (with-out-str (pprint species)))
   (for [evolves evolves-to]
     ($ evolution {:key (get-in evolves [:pokemon.evolution/species :pokemon/name])
                   :species (:pokemon.evolution/species evolves)
                   :evolves-to (:pokemon.evolution/evolves-to evolves)}))))


(defnc evolutions
  [{:keys [id]}]
  (let [{:keys [data loading?]} (exo.hooks/use-query (evolution-chain-query id))
        evolution-chain (get-in
                         data
                         [[:pokemon/id id]
                          :pokemon/species
                          :pokemon.species/evolution-chain
                          :pokemon.evolution/chain])]
    (if (empty? evolution-chain)
      (d/div "Loading...")
      ($ evolution {:species (:pokemon.evolution/species evolution-chain)
                    :evolves-to (:pokemon.evolution/evolves-to evolution-chain)}))))


(defnc app
  []
  (let [[id set-id] (hooks/use-state 1)
        [show-evolution-chain? set-show-evolution-chain] (hooks/use-state false)
        {:keys [data loading?]} (exo.hooks/use-query (pokemon-query id))]
    (d/div
     (d/button
      {:on-click #(set-id dec)
       :disabled (= 1 id)}
      "Prev")
     (d/button {:on-click #(set-id inc)} "Next")
     (d/div
      (d/img {:src (get-in data [[:pokemon/id id] :pokemon/sprites :pokemon.sprites/front-default])})
      (d/code
       (d/pre
        {:style {:display "inline-block"}}
        (with-out-str
          (pprint data)))))
     (d/div
      (d/button
       {:on-click #(set-show-evolution-chain not)}
       "Toggle evolution chain")
      (when show-evolution-chain?
        ($ evolutions {:id id}))))))


(defonce root (rdom/createRoot (js/document.getElementById "app")))

(defn ^:dev/after-load reload
  []
  (.render root ($ exo.hooks/provider {:config exo-config}
                   ($ app))))

(defn start
  []
  (.then (exo/preload! exo-config (pokemon-query 1)) #(reload)))
