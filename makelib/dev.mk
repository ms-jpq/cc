.PHONY: dev dev-clj dev-cljs dev-css

dev: dev-env dev-clj dev-cljs dev-css

target/public/index.html: clj/dev/index.html
	mkdir --parents -- "$$(dirname -- '$@')"
	cp --recursive --force -- '$<' '$@'

target/public/css:
	mkdir -p -- "$@"

dev-clj:
	watchexec --shell none --restart  --stop-timeout 0 -- clojure -M:run-clj --port 9090

dev-cljs: target/public/css target/public/index.html
	clojure -M:dev-cljs

dev-css: dev-env
	watchexec --shell none --restart -- node_modules/.bin/tailwindcss --input ./css/site.scss --output ./target/public/css/site.css
