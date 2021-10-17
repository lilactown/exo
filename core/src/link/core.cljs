(ns link.core
  (:require
   [autonormal.core :as a]))


;;
;; Networking
;;


(defprotocol IRequest
  (-then [req cb])
  (-catch [req cb]))


(extend-type js/Promise
  IRequest
  (-then [p cb]
    (.then p cb))
  (-catch [p cb]
    (.catch p cb)))


(def pending :pending)
(def resolved :resolved)
(def rejected :rejected)


(defrecord RequestState [status value])


;; This machinery is meant to work in such a way that it will be compatible
;; with React's Suspense Cache, but agnostic from it.
;; The idea is that we have the ability to create a mutable request store,
;; and each record in that request store is a map from some ID - in our case,
;; the EQL query the user is attempting to load - to a value that represents the
;; current status of the request.
;;
;; The `create-request-store` fn is used to create an opaque type that can serve
;; as this request store. I use an atom for ease of development rn. This
;; function, when used with React's Cache, will be used to uniquely identify the
;; cache strategy to use, as well as instantiate the initial cache.
;;
;; Currently, I am following the react-fetch implementation pretty closely.
;; In this they store a mutable object in the cache map which they update as the
;; request proceeds (rejects, fulfills). I am reproducing this by creating an
;; atom for each request and storing the state within that atom. This means that
;; we have atoms stored within the request-store atom; not ideal, but matches
;; the design of react-fetch, which is more useful for me rn.


;; https://github.com/facebook/react/blob/main/packages/react-fetch/src/ReactFetchBrowser.js#L38
;; `createRecordMap(): Map<string, Record>`
(defn create-request-store
  "Creates a new request ref store. In this we will store the current state
  of the request."
  []
  (atom {}))


(defn- pending?
  [req-state]
  (= pending (:status req-state)))


(defn- request->ref
  [request]
  (let [*req (atom (->RequestState pending request))]
    (-> request
        (-then
         (fn [value]
           (when (pending? @*req)
             (swap! *req assoc
                    :status resolved
                    :value value))))
        (-catch
         (fn [error]
           (when (pending? @*req)
             (swap! *req assoc
                    :status rejected
                    :value error)))))
    *req))


(comment
  (request->ref
   (reify IRequest
     (-then [t _] t) (-catch [t _] t))))


(defn load-state!
  [{:keys [request-store
           data-cache
           network]
    :as _config}
   query
   opts]
  ;; TODO here we should do deduping
  (let [*req (-> query
                 (network opts)
                 (doto (-then
                        (fn [data]
                          (swap! data-cache a/add data))))
                 (request->ref))]
    (swap! request-store assoc-in [query opts] *req)
    *req))


(defn preload!
  [config query opts]
  (load-state! config query opts)
  nil)
