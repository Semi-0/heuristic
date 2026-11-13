(ns lain-harness.catalog-selection
  "Read saved source for a model-selected identity. Compilation stays in Lain."
  (:require [datomic.client.api :as d]
            [lain-harness.source-restore :as restore]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]))

(def module-ids [:archive/build :archive/compose :archive/accumulated])

(defn selected-bundle [store id]
  (let [archives (into (array-map)
                       (for [module module-ids]
                         [module (if (:catalog/source-chunk
                                      (d/pull (d/db (:connection store)) [:catalog/source-chunk] [:catalog/id module]))
                                   (restore/read-archive store module)
                                   {:source "" :receipts []})]))
        bundle (restore/build-bundle archives id)]
    (swap! (:journal store) conj {:operation :select-source :id id
                                  :live-before (vec (keys @(:references store)))
                                  :operators (:operators bundle)
                                  :source-hashes (mapv :sha256 (:modules bundle))})
    bundle))

(defn facilities [store]
  (session/session-extension
   {:id ::selection
    :primitives {'catalog:selected-source
                 (connected/primitive 'catalog:selected-source (partial selected-bundle store))}}))
