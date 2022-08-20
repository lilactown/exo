(ns exo.http-example.app
  (:require
   ["react-dom" :as rdom]
   [exo.core :as exo]
   [exo.hooks :as exo.hooks]
   [exo.network.http :as exo.http]
   [exo.http-example.api :as example.api]
   [helix.core :refer [defnc $]]
   [helix.dom :as d]
   [helix.hooks :as hooks]
   [lambdaisland.fetch.edn]))


(defonce exo-config
  (exo/create-config
   {:network [(exo.http/link
               {:default-body-handler
                (fn [_ body]
                  (exo.http/transform-keys
                   body
                   example.api/pokemon-key-map))})]}))


(defn pokemon-query
  [id]
  `[{(:http/req
      {:uri ~(str "https://pokeapi.co/api/v2/pokemon/" id "/")
       :content-type :json})
     [:pokemon/id :pokemon/name :pokemon/weight]}])


(defnc app
  []
  (let [[pokemon set-pokemon] (hooks/use-state 1)
        {:keys [data loading?]} (exo.hooks/use-query (pokemon-query pokemon))]
    (d/div
     (d/input
      {:type "number"
       :value pokemon
       :on-change #(set-pokemon (.. % -target -value))})
     (d/div (pr-str loading?))
     (d/div (pr-str data)))))


(defonce root (rdom/createRoot (js/document.getElementById "app")))


(defn ^:dev/after-load start
  []
  (.render root (helix.core/provider
                 {:context exo.hooks/exo-config-context
                  :value exo-config}
                 ($ app))))
