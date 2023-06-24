.PHONY: dev dev-clj dev-css

dev: dev-env dev-clj dev-css

target/public/index.html: clj/dev/index.html
	mkdir --parents -- "$$(dirname -- '$@')"
	cp --recursive --force -- '$<' '$@'

dev-env:
	mkdir --parents -- target/public/css

dev-clj: dev-env target/public/index.html
	clojure -M:dev

dev-css: dev-env
	watchexec --shell none --restart -- node_modules/.bin/tailwindcss --input ./css/site.scss --output ./target/public/css/site.css
