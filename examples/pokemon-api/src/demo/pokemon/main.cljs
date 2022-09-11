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
  [{[:pokemon/id id]
    [:pokemon/id
     :pokemon/name
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
           {:pokemon.evolution/evolves-to '...}]}]}]}]}])


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


(defn- get-chain
  [query-result]
  (-> (first query-result) ; we don't depend on the id, so we can
      (val) ; render previous query results during fetch
      (get-in [:pokemon.species/evolution-chain
               :pokemon.evolution/chain])))


(defnc evolutions
  [{:keys [id]}]
  (let [{:keys [data loading?]} (exo.hooks/use-deferred-query
                                 (evolution-chain-query id))]
    (if (and (seq data) (get-chain data))
      (let [evolution-chain (get-chain data)]
        (d/div
         {:style {:opacity (if loading? 0.6 1)}}
         ($ evolution {:species (:pokemon.evolution/species evolution-chain)
                       :evolves-to (:pokemon.evolution/evolves-to evolution-chain)})))
      (if loading?
        (d/div "Loading...")
        "No evolution data"))))


(defnc app
  []
  (let [[id set-id] (hooks/use-state 1)
        [show-evolution-chain? set-show-evolution-chain] (hooks/use-state false)
        {:keys [data loading?]} (exo.hooks/use-deferred-query (pokemon-query id))]
    (d/div
     (d/button
      {:on-click #(set-id dec)
       :disabled (= 1 id)}
      "Prev")
     (d/input {:on-change #(set-id (js/parseInt (.. % -target -value)))
               :value (str id)
               :min 1
               :type "number"})
     (d/button {:on-click #(set-id inc)} "Next")
     (if (seq data)
       (d/div
        {:style {:opacity (if loading? 0.6 1)}}
        (d/img {:src (-> (first data)
                         (val)
                         (get-in [:pokemon/sprites
                                  :pokemon.sprites/front-default]))})
        (d/code
         (d/pre
          {:style {:display "inline-block"}}
          (with-out-str
            (pprint data)))))
       (d/div "Pokemon not found"))
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
