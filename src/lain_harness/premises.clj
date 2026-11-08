(ns lain-harness.premises
  (:require [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.host :as host]
            [propagators.datastructures.support-collection :as collection]
            [propagators.datastructures.layered-value :as datum]
            [propagators.network :as net]
            [propagators.cells.value :as value]))
(defn content [id base epoch status]
  (collection/content {:base base :support #{{:source id :timestamp epoch :premises-status status}}}))
(defn admit! [owner declaration]
  (let [receipt @(host/submit-extension! owner (session/session-extension declaration))]
    (when-not (= :admitted (:status receipt))
      (throw (ex-info (or (:message receipt) "Extension rejected") receipt)))
    declaration))
(defn source! [owner id base]
  ;; Source endpoints are declared by Lain; values enter through publication.
  (when-not (contains? (net/net-env (session/network (host/current-session owner))) id)
    (throw (ex-info "Declare the premise source in Lain before publishing" {:id id})))
  (when-not (= :accepted (host/submit! owner (session/cell-input id (content id base 1 :active))))
    (throw (ex-info "Premise ingress backpressured" {})))
  (host/step! owner)
  id)
(defn publish! [owner id epoch status]
  (let [v (net/network-cell-strongest (session/network (host/current-session owner)) id)
        base (datum/layer-value v :base)]
    (when-not (and (pos-int? epoch) (#{:active :retracted} status) (datum/layer-present? v :base))
      (throw (ex-info "Invalid premise publication" {:id id :epoch epoch :status status})))
    (when-not (= :accepted (host/submit! owner (session/cell-input id (content id base epoch status))))
      (throw (ex-info "Premise ingress backpressured" {})))))
(defn available? [owner sources]
  (let [network (session/network (host/current-session owner))]
    (every? #(not (value/unusable? (net/network-cell-strongest network %))) sources)))
(defn support-sources [events]
  (into #{} (map :source) (mapcat :support events)))
