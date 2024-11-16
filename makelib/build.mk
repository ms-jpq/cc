.PHONY: build

out/web:
	mkdir -p -- '$@'

out/web/site.css: $(shell shopt -u failglob && printf -- '%s ' ./clj/**/*.clj ./css/*) | out/web
	node_modules/.bin/tailwindcss --minify --input css/site.scss --output '$@'

out/web/main.js: $(shell shopt -u failglob && printf -- '%s ' ./*.edn ./clj/**/*.clj{c,s}) | out/web
	clojure -M:build-cljs

out/uber.jar: out/web/site.css out/web/main.js $(shell shopt -u failglob && printf -- '%s ' ./*.{clj,edn} ./clj/**/*.cl{j,jc})
	clojure -T:build-clj uber

out/run.jar: out/uber.jar
	{
		printf -- '%s\n' '#!/usr/bin/env -- java -jar'
		cat -- '$<'
	} > '$@'
	chmod +x -- '$@'

build: out/run.jar
