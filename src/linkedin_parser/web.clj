(ns linkedin-parser.web
  (:require [compojure.core :refer [defroutes POST]]
            [immutant.web :as web]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.util.response :refer [response]]
            [environ.core :refer [env]]
            [linkedin-parser.core :refer [parse-pdf]])
  (:gen-class))

(defn profile [file]
  (-> file parse-pdf response))

(defroutes app-routes
  (wrap-multipart-params
    (POST "/linkedin" {params :params}
      (profile (get-in params ["file" :tempfile])))))

(def app
  (->
    #'app-routes
    (wrap-defaults api-defaults)
    wrap-restful-response))

(defonce server (atom nil))
(defn stop-server! []
  (when-not (nil? @server)
    (web/stop @server)
    (reset! server nil)))

(defn run-server! [port]
  (do
    (reset! server (web/run app {:port port :host "0.0.0.0"}))
    (println (str "Web server running at http://localhost:" port))))

(defn -main [& [port]]
  (run-server! (Integer. (or port (env :port) 5000))))