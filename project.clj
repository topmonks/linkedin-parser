(defproject linkedin-parser "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/topmonks/linkedin-parser"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :jvm-opts ^:replace ["-Xms512m" "-Xmx512m" "-server"]
  :main linkedin-parser.web
  :dependencies [[org.clojure/clojure "1.8.0-RC5"]
                 [enlive "1.1.6"]
                 [environ "1.0.1"]
                 [http-kit "2.1.19"]
                 [compojure "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring-middleware-format "0.7.0"]
                 [org.apache.pdfbox/pdfbox "1.8.10"]]
  :java-source-paths ["src"]
  :plugins [[lein-environ "1.0.1"]]
  :profiles {:uberjar {:aot :all
                       :omit-source true}})
