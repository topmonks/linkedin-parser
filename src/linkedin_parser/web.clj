(ns linkedin-parser.web
  (:require [compojure.core :refer [defroutes POST]]
            [org.httpkit.server :as http]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.util.response :refer [response]]
            [environ.core :refer [env]]
            [linkedin-parser.core :refer [parse-pdf]])
  (:gen-class))

(defn profile [file]
  (->
    file
    parse-pdf
    response))

(defroutes app-routes
  (wrap-multipart-params
    (POST "/linkedin" {params :params} (profile (get-in params ["file" :tempfile])))))

(def app
  (->
    #'app-routes
    (wrap-defaults api-defaults)
    wrap-restful-response))

(defonce server (atom nil))
(defn stop-server! []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn run-server! [port]
  (do
    (reset! server (http/run-server app {:port port}))
    (println (str "Web server running at http://localhost:" port))))

(defn -main [& [port]]
  (run-server! (Integer. (or port (env :port) 5000))))